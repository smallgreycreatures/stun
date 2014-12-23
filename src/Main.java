import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;


public class Main {
	
	private DatagramSocket socket;
	private InetSocketAddress serverAddress;
	
	public static void main(String[]args) {
		Main m = new Main();
		m.process();
		
	}
	
	public void process() {
		try {
			socket = new DatagramSocket(4200);
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		serverAddress = new InetSocketAddress("192.168.1.132", 4200);
		try {
			Client client = new Client(serverAddress, socket);
			InetSocketAddress address = client.getMappedAddress();
			System.out.println(address.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
}
