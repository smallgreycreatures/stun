import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
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
		Server server = new Server();
		try {
			server.startServer();
		} catch (IOException e) {
			e.printStackTrace();
		}

		testClient(ipAddress, port, clientPort);
		
		server.shutdown();
		ArrayList<InetAddress> inetList = Server.getInetList();

		for(InetAddress i: inetList) {
			System.out.println(i);
		}
		
		Server server2 = new Server(inetList.get(1), port);
		
		try {
			server2.startServer();
		} catch (IOException e) {

			e.printStackTrace();
		}
		
		testClient(ipAddress, port, clientPort+1);
		
		server2.shutdown();
		
		System.out.println("SUCCESS!");
	}

	public void testClient(String ipAddress, int port, int clientPort) {
		InetSocketAddress serverAddress = new InetSocketAddress(ipAddress, port);
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket(clientPort);
		} catch (SocketException e1) {
			e1.printStackTrace();
		}

		Client client = new Client(serverAddress, socket);
		client.start();
		
		try {
			InetSocketAddress address = client.getMappedAddress();
			
			System.out.println("Your public address is: " + address);
		} catch (IOException e) {
			e.printStackTrace();
		}


	}

	public static void main(String[]args) {
		TestServer test = new TestServer();
		test.testServer();
	}
}
