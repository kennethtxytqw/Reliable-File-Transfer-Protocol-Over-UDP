import java.io.IOException;

public interface TransportProtocol {
    byte[] getData() throws IOException, InterruptedException;

    boolean hasNextPacket();

    char[] hexArray = "0123456789ABCDEF".toCharArray();

    default String bytesToHex(byte[] bytes, int len) {
        return bytesToHex(bytes,0,len);
    }

    default String bytesToHex(byte[] bytes, int offset, int len) {
        char[] hexChars = new char[len * 2];
        for (int j = offset; j < len+offset; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
