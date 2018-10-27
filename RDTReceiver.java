import java.io.IOException;
import java.net.SocketException;

/**
 * Able to receive packets in the presence of packet corruption
 */
public class RDTReceiver extends RDT implements IReceiver {
    public RDTReceiver(int port) throws SocketException {
        super();
        setPort(port);
        this.start();
    }


    @Override
    public void close() throws IOException {
        this.receivedLastPacket();
    }
}
