import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


public class UDPClient implements Runnable {

	private static final Logger logger = Logger.getLogger(UDPClient.class.getName());
	private static ConsoleHandler consoleHandler = new ConsoleHandler();

	private static final int TIMEOUT = 500;

	private int retries = 5;

	private InetSocketAddress serverAddress;
	private DatagramSocket datagramSocket;

	private InetSocketAddress mappedAddress;

	private boolean done;

	public UDPClient(InetSocketAddress serverAddress, DatagramSocket datagramSocket) {

		this.serverAddress = serverAddress;
		this.datagramSocket = datagramSocket;
		logger.log(Level.FINE, "Starting STUN UDP client on " + serverAddress);


	}

	public static void connectConsoleHandler() {
		logger.addHandler(consoleHandler);
	}

	public static void setLogLevel(Level newLevel) {
		logger.setLevel(newLevel);
	}

	public static void setConsoleHandlerLevel(Level newLevel) {
		consoleHandler.setLevel(newLevel);
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

			sb.append(" for " + datagramSocket.getLocalAddress() + ":" + datagramSocket.getLocalPort());

			throw new IOException(sb.toString());
		}

		logger.log(Level.FINE, "mapped address is " + mappedAddress);

		return mappedAddress;
	}

	private synchronized void done() {
		done = true;
		notifyAll();
	}

	public void run() {
		int socketTimeout = TIMEOUT;

		logger.log(Level.FINE, "using STUN server " + serverAddress);

		try {
			if (datagramSocket != null) {
				datagramSocket.setSoTimeout(socketTimeout);
			} 

			for (int i = 0; i < retries; i++) {

				//Prepare and send stun request
				try {
					logger.log(Level.FINE, "Sending STUN request");
					byte[] request = prepareRequest();
					send(request);
				} catch (IOException e) {
					e.printStackTrace();
				}

				try {
					byte[] response = getResponse();
					setMappedAddress(response);
					retries = 0;
				} catch (SocketTimeoutException e) {
					logger.warning("Socket waited for " + socketTimeout +"ms. No response.");
				} catch (IOException e) {
					e.printStackTrace();
				}

				//If time out just try again after t*2
				socketTimeout = socketTimeout*2;
				datagramSocket.setSoTimeout(socketTimeout);
			} 
		} catch (SocketException e) {
			e.printStackTrace();
		}
		done();
	}

	/**
	 * Prepares the STUN binding request to be sent over UDP
	 * @return byte array of STUN message.
	 */
	private byte[] prepareRequest() {
		byte[] request = new byte[Header.LENGTH + Header.TYPE_LENGTH_VALUE + Header.MAPPED_IPV4_ADDRESS_LENGTH];

		logger.log(Level.FINE, "StunClient: asking STUN server " + serverAddress.getAddress() + ":" + serverAddress.getPort() 
				+ " to get mapping for " + datagramSocket.getLocalAddress() +":" + datagramSocket.getLocalPort());

		Header.addTypeAndLengthTo(request);
		Header.addMagicCookieTo(request);
		Header.addTransactionIDTo(request);

		return request;
	}

	/**
	 * sends the Stun binding request 
	 * @param request
	 * @throws IOException
	 */
	private void send(byte[] request) throws IOException {

		if (serverAddress.getAddress() == null) {
			throw new IOException("Invalid stun server address: null ");
		}

		DatagramPacket packet = new DatagramPacket(request, request.length, serverAddress.getAddress(), serverAddress.getPort());

		logger.log(Level.FINER, "local addr " + datagramSocket.getLocalAddress() + " local port: " + datagramSocket.getLocalPort());
		datagramSocket.send(packet);

		logger.log(Level.FINE, "Packet sent! Length: " + packet.getLength());
	}

	private byte[] getResponse() throws IOException, SocketTimeoutException {
		logger.log(Level.FINE, "Waiting for response");
		byte[] response = new byte[1024];

		DatagramPacket packet = new DatagramPacket(response, response.length);
		datagramSocket.receive(packet);
		logger.log(Level.FINE, "Packet recieved.");

		logger.log(Level.FINE, "Got response to local address:" + datagramSocket.getLocalAddress()
				+ ":"+ datagramSocket.getLocalPort() + " Length: " + packet.getLength());
		return response;
	
	}
	
	private void setMappedAddress(byte[] response) throws SocketTimeoutException {
		
		int type = (int) ((response[0] << 8 & 0xff00) | (response[1] & 0xff));
		
		if (type == Header.BINDING_RESPONSE) {
			logger.log(Level.FINE, "Setting mappedAddress.");
			mappedAddress = Header.getAddress(response, Header.MAPPED_ADDRESS);
			return;
		}
		
		throw new SocketTimeoutException("BAD STUN RESPONSE");
	}
} 
