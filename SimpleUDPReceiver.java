import java.io.IOException;
import java.net.SocketException;

public class SimpleUDPReceiver extends UDP implements IReceiver {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: SimpleUDPReceiver <port>");
            System.exit(-1);
        }
        int port = Integer.parseInt(args[0]);
        SimpleUDPReceiver receiver = new SimpleUDPReceiver(port);
        while (true)
        {
            receiver.getData();
        }
    }

    public SimpleUDPReceiver(int port) throws SocketException {
        super();
        setPort(port);
    }

    @Override
    public void close() throws IOException {

    }
}
