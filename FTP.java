public interface FTP {
    public int MAX_FILENAME_LENGTH = 256;
    public static final char NULL_CHAR= '\0';
    public int MAX_FILENAME_LENGTH_IN_BYTES = MAX_FILENAME_LENGTH * Character.BYTES;
    public static final char TRUE_FRAG_FLAG = 'T';
    public static final char FALSE_FRAG_FLAG = 'F';

    public int FRAG_FLAG_LENGTH_IN_BYTES = Character.BYTES;
}
