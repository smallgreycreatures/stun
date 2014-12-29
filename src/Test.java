import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;


public class Test {
	
	private DatagramSocket datagramSocket;
	private InetSocketAddress serverAddress;
	private Socket socket;
	public static void main(String[]args) {
		Test t = new Test();
		
		t.testTCP();
		
		
	}
	public void testTCP() {

		try {
			socket = new Socket("stun.stunprotocol.org", 3478);
			
			TCPClient client = new TCPClient(socket);
			
			client.run();
			
			InetSocketAddress address = client.getMappedAddress();
			System.out.println("Your global TCP address is:" + address.toString());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	public void process() {

		try {
			datagramSocket = new DatagramSocket(4212);
		} catch (SocketException e1) {
			e1.printStackTrace();
		}
		
		//Public STUN server available through the STUNTMAN project on stun.stunprotocol.org: 3478 
		serverAddress = new InetSocketAddress("192.168.1.132", 3478);
		
		try {
			UDPClient client = new UDPClient(serverAddress, datagramSocket);
			client.run();
			InetSocketAddress address = client.getMappedAddress();
			System.out.println("Your global UDP address is:" + address.toString());
		} catch (IOException e) {

			e.printStackTrace();
		}
		
		
	}
}
