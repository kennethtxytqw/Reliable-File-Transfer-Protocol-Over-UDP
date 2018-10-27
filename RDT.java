import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class RDT extends UDP implements Closeable {


    public static final int SO_TIMEOUT = 100;
    private static final int RCV_BUFFER_WINDOW = 8;
    private static final int SEND_BUFFER_WINDOW = 8;
    private static int HEADER_LENGTH = UDP.getHeaderLength() + getSequenceNumberLength() * 2;
    private static int MAX_PKT_SIZE = getMaxBufferSize() - getHeaderLength();
    private int lastSentPacket = -1;
    private Map<Integer, DatagramPacket> sendBuffer;
    private Map<Integer, DatagramPacket> rcvBuffer;
    private int rcvBufferBase = 0;
    private int sendBufferBase = 0;
    private final Semaphore dataAvailable = new Semaphore(0, false);
    private final Semaphore rcvBufferAvailable = new Semaphore(RCV_BUFFER_WINDOW, false);
    private final Semaphore sendBufferAvailable = new Semaphore(SEND_BUFFER_WINDOW, false);
    private final Semaphore unAckAvailable = new Semaphore(0, false);
    private RcvThread rcvThread;
    private SendThread sendThread;

    private boolean toTerminate;
    private boolean doneReceiving;

    public RDT() {
        super();
        this.sendBuffer = new HashMap<>();
        this.rcvBuffer = new HashMap<>();

        toTerminate = false;
        doneReceiving = false;
    }

    public void start() {
        this.rcvThread = new RcvThread(this);
        rcvThread.start();
        this.sendThread = new SendThread(this);
        sendThread.start();
    }

    public static int getHeaderLength() {
        return HEADER_LENGTH;
    }

    public static int getSequenceNumberLength() {
        return Integer.BYTES;
    }

    @Override
    public int getMaxPktSize() {
        return MAX_PKT_SIZE;
    }


    @Override
    public byte[] getData() {
        DatagramPacket datagramPacket = null;
        try {
            System.err.println("Checking if any new data is available.");
            dataAvailable.acquire();
            System.err.println("There are " + rcvBuffer.size() + " data available.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        while (datagramPacket == null) {
            synchronized (rcvBuffer) {
                if (rcvBuffer.containsKey(rcvBufferBase)) {
                    datagramPacket = rcvBuffer.remove(rcvBufferBase);
                    rcvBufferBase++;
                    rcvBufferAvailable.release();
                }
            }
        }
        return extractData(datagramPacket);
    }

    public byte[] extractData(DatagramPacket datagramPacket) {
        return Arrays.copyOfRange(datagramPacket.getData(),
                                  getHeaderLength(),
                                  datagramPacket.getLength());
    }

    public void receiveAndStore() throws IOException {

        DatagramPacket datagramPacket = receive();
        while (isCorrupted(datagramPacket)) {
            datagramPacket = receive();
        }
        setAddr(datagramPacket.getAddress().getHostName(), datagramPacket.getPort());
        store(datagramPacket);
        confirmAck(datagramPacket);
    }

    public void confirmAck(DatagramPacket datagramPacket) {
        int ackNum = getAckNum(datagramPacket);
        System.err.println("Received Ack " + ackNum);
        synchronized (sendBuffer) {
            if (sendBufferBase > ackNum) {
                return;
            }

            for (int i = sendBufferBase; i <= ackNum; i++) {
                if (sendBuffer.containsKey(i)) {
                    sendBuffer.remove(i);
                    sendBufferAvailable.release();
                    sendBufferBase = i + 1;
                }
            }
        }
    }

    public int getAckNum(DatagramPacket datagramPacket) {
        ByteBuffer b = ByteBuffer.wrap(datagramPacket.getData());
        int ackNum = b.getInt(getChecksumLength() + getSequenceNumberLength());
        return ackNum;
    }

    public void store(DatagramPacket datagramPacket) throws IOException {
        int seqNum = getSeqNum(datagramPacket);
        System.err.println("Received pkt " + seqNum);

        try {
            rcvBufferAvailable.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        synchronized (rcvBuffer) {
            if (seqNum < rcvBufferBase || seqNum >= rcvBufferBase + RCV_BUFFER_WINDOW || rcvBuffer.containsKey(
                    seqNum)) {
                System.err.println("Dropping Pkt " + seqNum);
                rcvBufferAvailable.release();
            } else {
                System.err.println("Storing pkt " + seqNum);
                rcvBuffer.put(seqNum, datagramPacket);

                // Allow reading of data once there is no gap.
                if (rcvBufferBase == seqNum) {
                    for (int i = rcvBufferBase; i <= rcvBufferBase+RCV_BUFFER_WINDOW; i++) {
                        if (!rcvBuffer.containsKey(i)) break;
                        dataAvailable.release();
                    }
                } else if (rcvBuffer.containsKey(rcvBufferBase)) {
                    dataAvailable.release();
                }
            }
        }
        cumulativeAck();
    }

    public void cumulativeAck() throws IOException {
        int toAck;
        synchronized (rcvBuffer) {
            toAck = rcvBufferBase - 1;
            for (int i = rcvBufferBase; i <= rcvBufferBase + RCV_BUFFER_WINDOW; i++) {
                if (!rcvBuffer.containsKey(i)) {
                    toAck = i - 1;
                    break;
                }
            }
        }
        if (toAck >= 0) {
            if (doneReceiving) {
                ack(toAck);
            } else {
                ack(toAck-1);
            }
        }
    }

    public int getSeqNum(DatagramPacket datagramPacket) {
        ByteBuffer b = ByteBuffer.wrap(datagramPacket.getData());
        int seqNum = b.getInt(getChecksumLength());
        return seqNum;
    }


    public void ack(int sequenceNumber) throws IOException {
        System.err.println("Sending Ack " + sequenceNumber);
        ByteBuffer b = ByteBuffer.allocate(getSequenceNumberLength() * 2);
        b.putInt(-1);
        b.putInt(sequenceNumber);
        DatagramPacket datagramPacket = toDatagramPacketWithChecksum(b.array(),
                                                                     getSequenceNumberLength() * 2);
        sk.send(datagramPacket);
    }

    public void resend() throws IOException {
        if (sendBuffer.size()>0) {
            synchronized (sendBuffer) {

                for (DatagramPacket datagramPacket : sendBuffer.values()) {
                    System.err.println("Resending pkt " + getSeqNum(datagramPacket));
                    sk.send(datagramPacket);

                }
            }
        }
    }

    public void send(byte[] data, int len) throws IOException {
        try {
            sendBufferAvailable.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        byte[] wrappedData = wrapWithSeqNumAndAckNum(data, len);
        DatagramPacket datagramPacket = toDatagramPacketWithChecksum(wrappedData,
                                                                     wrappedData.length);
        synchronized (sendBuffer) {
            sendBuffer.put(getSeqNum(datagramPacket), datagramPacket);
        }

        System.err.println("Sending Pkt " + getSeqNum(datagramPacket));
        sk.send(datagramPacket);
        if (sendBuffer.size() <= 1) {
            unAckAvailable.release();
        }
    }

    public byte[] wrapWithSeqNumAndAckNum(byte[] data, int len) {
        ByteBuffer b = ByteBuffer.allocate(len + getSequenceNumberLength() * 2);
        b.putInt(++lastSentPacket);
        b.putInt(rcvBufferBase - 1);
        b.put(data, 0, len);
        return b.array();
    }


    public void terminate() {
        sk.disconnect();
    }

    @Override
    public void close() throws IOException {
        toTerminate = true;
        System.err.println("#######\nRDT to terminate: " + toTerminate + "\n######");
    }

    protected void receivedLastPacket() {
        doneReceiving = true;
    }

    public class RcvThread extends Thread {
        RDT rdt;

        public RcvThread(RDT rdt) {
            this.rdt = rdt;
        }

        public void run() {
            while (!rdt.toTerminate || sendBuffer.size() > 0) {
                try {
                    rdt.receiveAndStore();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.err.println("RcvThread still running...");
            }
            System.err.println("RcvThread terminated.");
        }
    }

    public class SendThread extends Thread {
        RDT rdt;

        public SendThread(RDT rdt) {
            this.rdt = rdt;
        }

        public void run() {
            int prevSendBufferBase = rdt.sendBufferBase;
            while (!rdt.toTerminate || rdt.sendBuffer.size() != 0) {
                if (prevSendBufferBase == rdt.sendBufferBase) {
                    try {
                        rdt.resend();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    System.err.println("No need to resend yet... Wait another 100ms");
                    prevSendBufferBase = rdt.sendBufferBase;
                }
                try {
                    sleep(SO_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                System.err.println("SendThread still running...");
            }
            rdt.terminate();
            System.err.println("SendThread terminated.");
        }
    }
}
