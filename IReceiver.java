import java.io.Closeable;
import java.io.IOException;

public interface IReceiver extends Closeable {

    byte[] getData() throws IOException;
    boolean hasNextPacket();

}
