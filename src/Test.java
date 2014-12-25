import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;


public class Test {
	
	private DatagramSocket socket;
	private InetSocketAddress serverAddress;
	
	public static void main(String[]args) {
		Test t = new Test();
		t.process();
		
	}
	
	public void process() {
		try {
			socket = new DatagramSocket(4202);
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		serverAddress = new InetSocketAddress("localhost", 4200);
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
