import java.io.IOException;

public class SimpleUDPSender extends UDP implements ISender {

	public static void main(String[] args) throws Exception 
	{
		if (args.length != 3) {
			System.err.println("Usage: SimpleUDPSender <host> <rcvport> <num_pkts>");
			System.exit(-1);
		}

		int num = Integer.parseInt(args[2]);
		SimpleUDPSender sender = new SimpleUDPSender();
		sender.setAddr(args[0], args[1]);
		byte[] data = new byte[20];

		for (int i = 1; i <= num; i++)
		{
			sender.send(data, data.length);
		}
	}

	@Override
	public void close() throws IOException {

	}
}
