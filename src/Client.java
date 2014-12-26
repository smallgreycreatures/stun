import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
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
	  ConsoleHandler consoleHandler = new ConsoleHandler();

	private boolean done;
	
	public Client(InetSocketAddress serverAddress, DatagramSocket datagramSocket) throws IOException {
	
		
		this.serverAddress = serverAddress;
		this.datagramSocket = datagramSocket;
		logger.addHandler(consoleHandler);
		
		logger.info("Starting stun client to " + serverAddress);
		start();
		
	}
	
	public InetSocketAddress getMappedAddress() throws IOException {
		
		synchronized(this) {
			while (!done) {
				try {
					wait();
				} catch (InterruptedException e) {
					throw new IOException("Failed to retrieve mapped address: Interrupted");
				}
				
			}
		}
		if (mappedAddress == null) {
			StringBuilder sb = new StringBuilder();
			sb.append("Failed to retrieve mapped address");
			
			if (socket != null) {
				sb.append(" for " + socket.getLocalAddress() + ":" + socket.getLocalPort());
			} else if (datagramSocket != null) {
				sb.append(" for " + datagramSocket.getLocalAddress() + ":" + datagramSocket.getLocalPort());
			}
			logger.warning(sb.toString());
			
			logger.warning("IF YOU ARE BEHIND A FIREWALL OR NAT, ADDRESSES ARE NOT LIKELY TO BE CORRECT");
			
			throw new IOException(sb.toString());
		}
		
		logger.info("mapped address is " + mappedAddress);
		
		return mappedAddress;
	}
	
	private synchronized void done() {
		done = true;
		notifyAll();
	}
	public void run() {
		int socketTimeout;
		
		logger.info("using STUN server " + serverAddress);
		
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
			
			
			//Prepare and send stun request
			try {
				logger.info("Sending STUN request " + i);
				sendRequest();
			} catch (IOException e) {
				logger.warning("Unable to send stun request: " + e.getMessage());
			}
			
			try {
				waitForResponse();
			} catch (SocketTimeoutException e) {
				logger.warning("No response to STUN request: " + e.getMessage());
			} catch (IOException e) {
				logger.warning("Recieve failed: " + e.getMessage());
			}
			
			try {
				if (datagramSocket != null) {
					datagramSocket.setSoTimeout(socketTimeout);
				} else {
					socket.setSoTimeout(socketTimeout);
				}
			} catch (SocketException e) {
				logger.warning("Unable to reset socket timeout: " + e.getMessage());
			}
		}
		System.out.println("Out of loop");
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
		
		logger.info("StunClient: asking STUN server " + serverAddress.getAddress() + ":" + serverAddress.getPort() 
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
		
		byte[] address = addressToMap.getAddress();
		buffer[28] = address[0];
		buffer[29] = address[1];
		buffer[30] = address[2];
		buffer[31] = address[3];
		
		if (datagramSocket != null) {
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverAddress.getAddress(), serverAddress.getPort());
			
			logger.info("local addr " + datagramSocket.getLocalAddress() + " local port: " + datagramSocket.getLocalPort());
			datagramSocket.send(packet);
			System.out.println("Packet sent!");
		} else {
			DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
			
			outputStream.write(buffer, 0, buffer.length);
			outputStream.flush();
		}
		
	}
	
	private void waitForResponse() throws IOException, SocketTimeoutException {
		System.out.println("Waiting for response");
		byte[] response = new byte[1000];
		
		for (int i = 0; i < 50; i++) {
			int length;
			
			if (datagramSocket != null) {
				DatagramPacket packet = new DatagramPacket(response, response.length);
				datagramSocket.receive(packet);
				System.out.println("packet recieved");
				length = packet.getLength();
			} else {
				length = input.read(response);
			}
			
			logger.info("Got response! " + length + " local address " + datagramSocket.getLocalAddress()
					+ " local port " + datagramSocket.getLocalPort());
			
			int type = (int) ((response[0] << 8 & 0xff00) | (response[1] & 0xff));
			
			if (type == Header.BINDING_RESPONSE) {
				mappedAddress = Header.getAddress(response, Header.MAPPED_ADDRESS);
				return;
			}
			
			logger.info("BAD STUN response, length " + length + " TCP " + (input != null));
		}
		throw new SocketTimeoutException("BAD STUN RESPONSE");
	}
} 
