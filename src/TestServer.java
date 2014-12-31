import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.logging.Level;


public class TestServer {
	//Public STUN server available through the STUNTMAN project on stun.stunprotocol.org: 3478
	
	public TestServer() {

	} 

	public void testServer() {
		String ipAddress = "192.168.1.132";
		int port = 3478;
		int clientPort = 4200;
		Server.setLogLevel(Level.FINE);
		Server.connectConsoleHandler();
		Server.setConsoleHandlerLevel(Level.FINE);
		UDPClient.setLogLevel(Level.FINE);
		UDPClient.connectConsoleHandler();
		UDPClient.setConsoleHandlerLevel(Level.FINE);
		TCPClient.setLogLevel(Level.FINE);
		TCPClient.connectConsoleHandler();
		TCPClient.setConsoleHandlerLevel(Level.FINE);
		Header.setLogLevel(Level.FINE);
		Header.connectConsoleHandler();
		Header.setConsoleHandlerLevel(Level.FINE);
		
		Server server = new Server();
		try {
			server.startServer();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		testUDPClient(ipAddress, port, clientPort);
		testTCPClient(ipAddress, port+1);
		try {
			server.shutdown();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		ArrayList<InetAddress> inetList = Server.getInetList();
		
		Server server2 = new Server(inetList.get(1), port);
		
		try {
			server2.startServer();
		} catch (IOException e) {

			e.printStackTrace();
		}
		testUDPClient(ipAddress, port, clientPort+1);
		testTCPClient(ipAddress, port+1);

		try {
			server2.shutdown();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("SUCCESS!");
	}

	public void testTCPClient(String serverAddress, int serverPort) {

		try {
			Socket socket = new Socket(serverAddress, serverPort);
			
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
	
	public void testUDPClient(String ipAddress, int port, int clientPort) {
		InetSocketAddress serverAddress = new InetSocketAddress(ipAddress, port);
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket(clientPort);
		} catch (SocketException e1) {
			e1.printStackTrace();
		}

		UDPClient client = new UDPClient(serverAddress, socket);
		client.run();
		
		try {
			InetSocketAddress address = client.getMappedAddress();
			
			System.out.println("Your global UDP address is: " + address);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[]args) {
		TestServer test = new TestServer();
		test.testServer();
	}
}
