import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * A Class that is made as an abstraction layer between the library and the application
 * using it. It is meant to make it easier to discover ones global IP address and port.
 * @author Frans
 *
 */
public class AddressFinder {
	
	/**
	 * Method that starts a stun client thread and sends a request to a STUN
	 * server through UDP. Thread safe.
	 * @param stunServerAddress The IP address to the STUN server the request should be sent to
	 * @param stunServerPort The port to the STUN server the request should be sent to
	 * @param clientPort The port that the client will be using 
	 * @return The Global IP address and Global Port
	 * @throws IOException For faulty IP address and DatagramSocket
	 */
	public static InetSocketAddress discoverUDPAddress(String stunServerAddress, int stunServerPort, int clientPort) throws IOException {
		InetSocketAddress serverAddress = new InetSocketAddress(stunServerAddress, stunServerPort);
		DatagramSocket socket = new DatagramSocket(clientPort);
		
		UDPClient client = new UDPClient(serverAddress, socket);
		
		client.run();
		
		InetSocketAddress mappedAddress = client.getMappedAddress();
		
		return mappedAddress;
	}
	
	/**
	 * Method that starts a STUN client thread and sends a request to a STUN
	 * server through UDP. Thread safe.
	 * @param stunServerAddress The IP address to the STUN server the request should be sent to
	 * @param stunServerPort The port to the STUN server the request should be sent to
	 * @param clientAddress The IP address that the client will be using
	 * @param clientPort The port that the client will be using 
	 * @return The Global IP address and Global Port
	 * @throws UnknownHostException for Socket
	 * @throws IOException for Socket
	 */
	public static InetSocketAddress discoverTCPAddress(String stunServerAddress, int stunServerPort, String clientAddress, int clientPort) throws UnknownHostException, IOException {
		
		Socket socket = new Socket(stunServerAddress, stunServerPort, InetAddress.getByName(clientAddress), clientPort);
		
		TCPClient client = new TCPClient(socket);
		
		client.run();
		
		InetSocketAddress mappedAddress = client.getMappedAddress();
		
		return mappedAddress;
	
	}
}
