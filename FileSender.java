import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class FileSender implements FTP, Closeable {
    private ISender sender;

    public FileSender(ISender sender) {
        this.sender = sender;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("Usage: FileSender <host> <rcvport> <src_file> <dst_filename>");
            System.exit(-1);
        }


        FileSender fileSender = new FileSender(new RDTSender(args[0], Integer.parseInt(args[1])));
        fileSender.send(args[2], args[3]);
        fileSender.close();
    }

    private void send(String src_file, String dst_filename) throws IOException {
        if (dst_filename.length() > MAX_FILENAME_LENGTH || dst_filename.length() == 0) {
            System.err.println("<dst_filename> has to be 1-255 characters long");
            System.exit(-1);
        }

        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src_file));
        send(bis, dst_filename);
    }

    private void send(BufferedInputStream bis, String dstFilename) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(sender.getMaxPktSize());
        if (bis.available() > b.array().length - FRAG_FLAG_LENGTH_IN_BYTES) {
            b.putChar(TRUE_FRAG_FLAG);
        } else {
            System.err.println("#### Last Frag ####");
            b.putChar(FALSE_FRAG_FLAG);
        }

        for (int i = 0; i < MAX_FILENAME_LENGTH; i++) {
            if (i < dstFilename.length()) {
                b.putChar(dstFilename.charAt(i));
            } else {
                b.putChar(NULL_CHAR);
            }
        }

        int bytesRead = bis.read(b.array(),
                                 MAX_FILENAME_LENGTH_IN_BYTES + FRAG_FLAG_LENGTH_IN_BYTES,
                                 b.array().length - MAX_FILENAME_LENGTH_IN_BYTES - FRAG_FLAG_LENGTH_IN_BYTES);
        send(b.array(), MAX_FILENAME_LENGTH_IN_BYTES + FRAG_FLAG_LENGTH_IN_BYTES + bytesRead);
        int count = 0;
        while (bis.available() > 0) {
            b.clear();
            if (bis.available() > b.array().length - FRAG_FLAG_LENGTH_IN_BYTES) {
                b.putChar(TRUE_FRAG_FLAG);
            } else {
                System.err.println("#### Last Frag ####");
                b.putChar(FALSE_FRAG_FLAG);
            }
            bytesRead = bis.read(b.array(),
                                 FRAG_FLAG_LENGTH_IN_BYTES,
                                 b.array().length - FRAG_FLAG_LENGTH_IN_BYTES);

            System.err.println("Sending part " + (++count));
            send(b.array(), bytesRead + FRAG_FLAG_LENGTH_IN_BYTES);
        }
        System.err.println("#### Done sending file ####");
    }

    private void send(byte[] data, int len) throws IOException {
        sender.send(data, len);
    }

    @Override
    public void close() throws IOException {
        sender.close();
    }
}
