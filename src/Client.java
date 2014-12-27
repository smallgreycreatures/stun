import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Client extends Thread {

	private static final Logger logger = Logger.getLogger(Client.class.getName());

	private static final int TIMEOUT = 500;

	private int retries = 5;

	private InetSocketAddress serverAddress;
	private DatagramSocket datagramSocket;

	private DataInputStream input;

	private InetSocketAddress mappedAddress;
	//private ConsoleHandler consoleHandler = new ConsoleHandler();

	private boolean done;

	public Client(InetSocketAddress serverAddress, DatagramSocket datagramSocket) throws IOException {


		this.serverAddress = serverAddress;
		this.datagramSocket = datagramSocket;
		//logger.addHandler(consoleHandler);
		System.out.println("Starting stun client to " + serverAddress);


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

		logger.info("mapped address is " + mappedAddress);

		return mappedAddress;
	}

	private synchronized void done() {
		done = true;
		notifyAll();
	}
	
	public void run() {
		int socketTimeout = TIMEOUT;

		logger.info("using STUN server " + serverAddress);

		try {
			if (datagramSocket != null) {
				datagramSocket.setSoTimeout(socketTimeout);
			} 

			for (int i = 0; i < retries; i++) {

				//Prepare and send stun request
				try {
					logger.info("Sending STUN request");
					byte[] request = prepareRequest();
					send(request);
				} catch (IOException e) {
					e.printStackTrace();
				}

				try {
					waitForResponse();
					retries = 0;
				} catch (SocketTimeoutException e) {
					logger.warning("Socket waited for " + socketTimeout +"ms. No response.");
				} catch (IOException e) {
					e.printStackTrace();
				}

				socketTimeout = socketTimeout*2;
				datagramSocket.setSoTimeout(socketTimeout);
			} 
		} catch (SocketException e) {
			e.printStackTrace();
		}
		done();
		System.out.println("Out of loop");
	}
	
	/**
	 * Prepares the STUN binding request to be sent over UDP
	 * @return byte array of STUN message.
	 */
	private byte[] prepareRequest() {
		byte[] request = new byte[Header.LENGTH + Header.TYPE_LENGTH_VALUE + Header.MAPPED_ADDRESS_LENGTH];

		logger.log(Level.FINE, "StunClient: asking STUN server " + serverAddress.getAddress() + ":" + serverAddress.getPort() 
				+ " to get mapping for " + datagramSocket.getLocalAddress() +":" + datagramSocket.getLocalPort());

		addTypeAndLengthTo(request);		
		addMagicCookieTo(request);
		addTransactionIDTo(request);
		addContentTo(request);
		
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

		System.out.println("local addr " + datagramSocket.getLocalAddress() + " local port: " + datagramSocket.getLocalPort());
		datagramSocket.send(packet);

		System.out.println("Packet sent! Length: " + packet.getLength());
	}
	
	public void addTypeAndLengthTo(byte[] request) {
		request[1] = (byte) Header.BINDING_REQUEST;
		request[3] = (byte) Header.TYPE_LENGTH_VALUE + Header.MAPPED_ADDRESS_LENGTH;
	}
	
	/**
	 * A STUN Header MUST contain a Magic cookie with the value of 0x2112A442
	 * according to RFC3489.
	 * @param request
	 */
	public void addMagicCookieTo(byte[] request) {
		int magicCookie = 0x2112A442;
		
		request[4] = (byte) (magicCookie >> 24 & 0xff);
		request[5] = (byte) (magicCookie >> 16 & 0xff);
		request[6] = (byte) (magicCookie >> 8 & 0xff);
		request[7] = (byte) (magicCookie & 0xff);
	}
	
	/**
	 * A transaction ID MUST be uniformly and randomly chosen 
	 * and should be cryptographically random.
	 * 
	 * @param request
	 */
	public void addTransactionIDTo(byte[] request) {
		SecureRandom rnd = new SecureRandom();
		byte rndBytes[] = new byte[12];
		rnd.nextBytes(rndBytes);
		
		for (int i = 0; i < 12; i++) {
			request[i+8] = rndBytes[0];
		}
	}
	
	/**
	 * Adds the address and port to be mapped to the request
	 * @param request
	 */
	public void addContentTo(byte[] request) {
		InetAddress myAddress = datagramSocket.getLocalAddress();
		int myPort = datagramSocket.getLocalPort();
		
		request[21] = Header.MAPPED_ADDRESS;
		request[23] = Header.MAPPED_ADDRESS_LENGTH;

		request[25] = 1; //address Family. Only serving IPv4

		request[26] = (byte) (myPort >> 8);
		request[27] = (byte) (myPort & 0xff);

		byte[] address = myAddress.getAddress();
		
		for (int i = 0; i < 4; i++) {
			request[28+i] = address[i];
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

			System.out.println("Got response! " + length + " local address " + datagramSocket.getLocalAddress()
					+ " local port " + datagramSocket.getLocalPort());

			int type = (int) ((response[0] << 8 & 0xff00) | (response[1] & 0xff));

			if (type == Header.BINDING_RESPONSE) {
				System.out.println("Setting mappedAddress");
				mappedAddress = Header.getAddress(response, Header.MAPPED_ADDRESS);
				return;
			}

			logger.info("BAD STUN response, length " + length + " TCP " + (input != null));
		}
		throw new SocketTimeoutException("BAD STUN RESPONSE");
	}
} 
