import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileReceiver implements FTP, Closeable {

    public IReceiver receiver;
    private ByteBuffer b;

    public FileReceiver(IReceiver receiver) {
        this.receiver = receiver;
        b = ByteBuffer.allocate(1500);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: FileReceiver <port>");
            System.exit(-1);
        }
        int port = Integer.parseInt(args[0]);
        FileReceiver fileReceiver = new FileReceiver(new RDTReceiver(port));
        fileReceiver.receive();
        fileReceiver.close();
    }

    public void receive() throws IOException {
        byte[] data = receiver.getData();

        b = ByteBuffer.wrap(data);
        boolean hasNext = b.getChar() == TRUE_FRAG_FLAG;
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < MAX_FILENAME_LENGTH; i++) {
            stringBuilder.append(b.getChar());
        }

        String pathname = stringBuilder.substring(0,
                                                  stringBuilder.indexOf(String.valueOf(Character.MIN_VALUE)));
        Path dstFilePath = Paths.get(pathname);
        System.err.println(dstFilePath);
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dstFilePath.toFile()));
        bos.write(b.array(),
                  MAX_FILENAME_LENGTH_IN_BYTES + FRAG_FLAG_LENGTH_IN_BYTES,
                  data.length - MAX_FILENAME_LENGTH_IN_BYTES - FRAG_FLAG_LENGTH_IN_BYTES);
        int count = 0;
        while (hasNext) {
            data = receiver.getData();
            System.err.println("Writing part " + (++count));
            bos.write(data, FRAG_FLAG_LENGTH_IN_BYTES, data.length - FRAG_FLAG_LENGTH_IN_BYTES);
            hasNext = ByteBuffer.wrap(data).getChar() == TRUE_FRAG_FLAG;
        }
        bos.close();
    }

    @Override
    public void close() throws IOException {
        receiver.close();
    }
}
