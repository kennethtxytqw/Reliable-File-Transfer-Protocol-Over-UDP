import java.io.IOException;
import java.net.SocketException;

/**
 * Able to send packets in the presence of packet corruption
 */
public class RDTSender extends RDT implements ISender{
    public RDTSender(String host, int port) throws SocketException {
        super();
        setAddr(host, port);
        setPort();
        this.start();
    }
}
