import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;

public abstract class UDP implements TransportProtocol {

    DatagramSocket sk;
    InetSocketAddress addr;

    private static int MAX_BUFFER_SIZE = 1000;
    private static int MAX_PKT_SIZE = MAX_BUFFER_SIZE - getChecksumLength();

    public UDP() {
    }


    public void setPort(int port) throws SocketException {
        sk = new DatagramSocket(port);
    }

    public void setAddr(InetSocketAddress addr) {
        this.addr = addr;
    }

    public void setPort() throws SocketException {
        this.sk = new DatagramSocket();
    }

    public void setAddr(String host, String rcvPort) {
        setAddr(host, Integer.parseInt(rcvPort));
    }

    public void setAddr(String host, int rcvPort) {
        setAddr(new InetSocketAddress(host, rcvPort));
    }

    public static int getChecksumLength() {
        return Long.BYTES;
    }

    public static int getMaxBufferSize() {
        return MAX_BUFFER_SIZE;
    }

    public static int getHeaderLength() {
        return getChecksumLength();
    }

    public int getMaxPktSize() {
        return MAX_PKT_SIZE;
    }

    public byte[] getData() throws IOException {
        DatagramPacket pkt = receive();
        if (isCorrupted(pkt)) {
            return new byte[]{};
        }
        return Arrays.copyOfRange(pkt.getData(), getChecksumLength(), pkt.getLength());
    }

    public boolean isCorrupted(DatagramPacket datagramPacket) {
        if (datagramPacket.getLength() < 8) {
            System.err.println("Pkt too short");
            return true;
        }
        byte[] data = datagramPacket.getData();
        ByteBuffer b = ByteBuffer.wrap(data);
        long receivedChecksum = b.getLong();
        long recalculatedChecksum = getCheckSum(b.array(),
                   getChecksumLength(),
                   datagramPacket.getLength() - getChecksumLength());
        // Debug output
//        System.err.println("Received CRC:" + crc.getValue() + " Data:" + bytesToHex(b.array(),
//                                                                                    datagramPacket.getLength() - getChecksumLength()));
        if (recalculatedChecksum != receivedChecksum) {
            System.err.println("Pkt corrupt");
            return true;
        }
        return false;
    }

    public long getCheckSum(byte[] array, int off, int len) {
        CRC32 crc = new CRC32();
        crc.reset();
        crc.update(array, off, len);
        return crc.getValue();
    }

    public DatagramPacket receive() throws IOException {
        DatagramPacket pkt = new DatagramPacket(new byte[MAX_BUFFER_SIZE], MAX_BUFFER_SIZE);
        sk.receive(pkt);
        return pkt;
    }

    @Override
    public boolean hasNextPacket() {
        return true;
    }

    public void send(byte[] data, int len) throws IOException {
        DatagramPacket pkt = toDatagramPacketWithChecksum(data, len);
        sk.send(pkt);
//        System.err.println("Sent CRC:" + chksum + " Contents:" + bytesToHex(data, len));
    }

    public DatagramPacket toDatagramPacketWithChecksum(byte[] data, int len) {
        ByteBuffer b = ByteBuffer.allocate(len + getChecksumLength());
        b.putLong(0);
        b.put(data, 0, len);
        b.rewind();

        long chksum =  getCheckSum(b.array(), getChecksumLength(), len);
        b.putLong(chksum);
        DatagramPacket pkt = new DatagramPacket(b.array(), len + getChecksumLength(), addr);
        return pkt;
    }
}
