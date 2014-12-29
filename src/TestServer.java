import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.logging.Level;


public class TestServer {

	public TestServer() {

	}

	public void testServer() {
		String ipAddress = "192.168.1.132";
		int port = 3478;
		Server.setLogLevel(Level.FINE);
		Server.connectConsoleHandler();
		Server.setConsoleHandlerLevel(Level.FINE);
		Server server = new Server();
		try {
			server.startServer();
		} catch (IOException e) {
			e.printStackTrace();
		}

		testClient(ipAddress, port);
		
		server.shutdown();
		ArrayList<InetAddress> inetList = Server.getInetList();

		for(InetAddress i: inetList) {
			System.out.println(i);
		}
	}

	public void testClient(String ipAddress, int port) {
		InetSocketAddress serverAddress = new InetSocketAddress(ipAddress, port);
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket(4200);
		} catch (SocketException e1) {
			e1.printStackTrace();
		}

		Client client = new Client(serverAddress, socket);
		client.start();
		
		try {
			InetSocketAddress address = client.getMappedAddress();
			
			System.out.println("Your public address is: " + address);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


	}

	public static void main(String[]args) {
		TestServer test = new TestServer();
		test.testServer();
	}
}
