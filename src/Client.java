import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.logging.Logger;


public class Client extends Thread {

	private static final Logger logger = Logger.getLogger(Client.class.getName());
	
	//Wait for 3 seconds
	private static final int TIMEOUT = 3000;
	private static final int RETRIES = 5;
	
	private static int timeout = TIMEOUT;
	private static int retries = RETRIES;
	
	private InetSocketAddress serverAddress;
	private DatagramSocket datagramSocket;
	
	private Socket socket;
	private DataInputStream input;
	
	private InetSocketAddress mappedAddress;
	
	private boolean done;
	
	public Client(InetSocketAddress serverAddress, DatagramSocket datagramSocket) throws IOException {
	
		
		this.serverAddress = serverAddress;
		this.datagramSocket = datagramSocket;
		
		logger.fine("Starting stun client to " + serverAddress);
		start();
		
	}
	
	private synchronized void done() {
		done = true;
		notifyAll();
	}
	
	public void run() {
		
		int socketTimeout;
		
		logger.fine("using STUN server " + serverAddress);
		
		try {
			if (datagramSocket != null) {
				socketTimeout = datagramSocket.getSoTimeout();
				datagramSocket.setSoTimeout(socketTimeout);
			} else {
				socketTimeout = datagramSocket.getSoTimeout();
				socket.setSoTimeout(timeout);
			} 
		} catch (SocketException e) {
			logger.warning("Unable to set socket timeout: " + e.getMessage());
			done();
			return;
		}
		
		for (int i = 0; i < retries; i++) {
			try {
				logger.fine("Sending STUN request " + i);
				sendRequest();
			} catch (IOException e) {
				logger.warning("Unable to send stun request: " + e.getMessage());
			}
		}
	}
	private void sendRequest() throws IOException {
		InetAddress addressToMap;
		int port;
		
		mappedAddress = null;
		
		//ahh, this is for evaluate if we are sending requests through UDP or TCP
		if (datagramSocket != null) {
			addressToMap = datagramSocket.getLocalAddress();
			port = datagramSocket.getLocalPort();
		} else {
			addressToMap = socket.getLocalAddress();
			port = socket.getLocalPort();
		}
		
		if (serverAddress.getAddress() == null) {
			throw new IOException("Invalid stun server address: null ");
		}
		
		logger.fine("StunClient: asking STUN server " + serverAddress.getAddress() + ":" + serverAddress.getPort() 
				+ " to get mapping for " + addressToMap.getHostAddress() +":" + port);
		
		byte[] buffer = new byte[Header.STUN_HEADER_LENGTH + Header.TYPE_LENGTH_VALUE + Header.MAPPED_ADDRESS_LENGTH];
		
		buffer[1] = (byte) Header.BINDING_REQUEST;
		buffer[3] = (byte) Header.TYPE_LENGTH_VALUE + Header.MAPPED_ADDRESS_LENGTH;
		
		long time = System.currentTimeMillis();
		
		//Substitut för Magic cookie + Transaction ID
		for (int i = 0; i < 16; i++) {
			buffer[i + 4] = (byte) (time >> ((i % 4) * 8)); 
		}
		
		buffer[21] = Header.MAPPED_ADDRESS;
		buffer[23] = Header.MAPPED_ADDRESS_LENGTH;
		
		buffer[25] = 1; //address Family
		
		buffer[26] = (byte) (port >> 8);
		buffer[27] = (byte) (port & 0xff);
		
	}
}
