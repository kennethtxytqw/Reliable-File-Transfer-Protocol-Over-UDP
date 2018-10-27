import java.io.Closeable;
import java.io.IOException;

public interface ISender extends Closeable {
    void send(byte[] data, int len) throws IOException;
    int getMaxPktSize();
}
