import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class that implements a STUN Client that sends a STUN request through TCP.
 * Implements Runnable and is ThreadSafe.
 * @author Frans
 *
 */
public class TCPClient implements Runnable {

	private static final Logger logger = Logger.getLogger(TCPClient.class.getName());
	private static ConsoleHandler consoleHandler = new ConsoleHandler();

	private static final int TIMEOUT = 500;

	private InetSocketAddress serverAddress;
	private Socket socket;
	private DataInputStream input;

	private InetSocketAddress mappedAddress;

	private boolean done;

	public TCPClient(Socket socket) {
		this.socket = socket;
		
		logger.log(Level.FINE, "Starting STUN TCP client on " + serverAddress);

		serverAddress = new InetSocketAddress(socket.getInetAddress(), socket.getPort());
		
		try {
			input = new DataInputStream(socket.getInputStream());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * For debugging reasons.
	 * Connect Handler to Logger in order to see Level.FINE messages
	 */
	public static void connectConsoleHandler() {
		logger.addHandler(consoleHandler);
	}

	/**
	 * For debugging reasons
	 * Setting the Level on the Logger
	 * @param newLevel
	 */
	public static void setLogLevel(Level newLevel) {
		logger.setLevel(newLevel);
	}
	
	/**
	 * For debugging reasons 
	 * Setting the Level on the ConsoleHandler
	 * @param newLevel
	 */
	public static void setConsoleHandlerLevel(Level newLevel) {
		consoleHandler.setLevel(newLevel);
	}
	
	/**
	 * Method that put the asking thread to sleep until a response has been accepted
	 * it then tries to deliver the Global IP address and Global port.
	 * If mapped address == null the request failed and an IOException will be thrown.
	 * @return Global IP address and Port number
	 * @throws IOException If mapped address == null the request failed and an IOException will be thrown.
	 */
	public InetSocketAddress getMappedAddress() throws IOException {

		synchronized(this) {
			while (!done) {
				try {
					wait();
				} catch (InterruptedException e) {
					throw new IOException("Failed to retrieve mapped address: Interrupted.");
				}

			}
		}
		if (mappedAddress == null) {
			StringBuilder sb = new StringBuilder();
			sb.append("Failed to retrieve mapped address");

			sb.append(" for " + socket.getLocalAddress() + ":" + socket.getLocalPort());

			throw new IOException(sb.toString());
		}

		logger.log(Level.FINE, "mapped address is " + mappedAddress);

		return mappedAddress;
	}

	private synchronized void done() {
		done = true;
		notifyAll();
	}
	/**
	 * Method that tries to send 5 requests with a increasing wait time after each in 
	 * order to follow the guidelines in RFC 5389. If it succeeds it will notify threads 
	 * waiting in getMappedAddress and then exit.
	 */
	public void run() {
		int socketTimeout = TIMEOUT;
		int retries = 5;
		
		logger.log(Level.FINE, "using STUN server " + serverAddress);

		try {
			if (socket != null) {
				socket.setSoTimeout(socketTimeout);
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
				socket.setSoTimeout(socketTimeout);
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
				+ " to get mapping for " + socket.getLocalAddress() +":" + socket.getLocalPort());

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

		
		logger.log(Level.FINER, "local addr " + socket.getLocalAddress() + " local port: " + socket.getLocalPort());
		DataOutputStream output = new DataOutputStream(socket.getOutputStream());
		
		output.write(request);
		logger.log(Level.FINE, "Request sent to " + socket.getInetAddress() + ":" + socket.getPort() +  " ! Length: " + request.length);
	}

	private byte[] getResponse() throws IOException {
		logger.log(Level.FINE, "Waiting for response");
		
		byte[] response = new byte[1024];
		
		int length = input.read(response);
		
		logger.log(Level.FINE, "Got response to local address:" + socket.getLocalAddress()
				+ ":"+ socket.getLocalPort() + " Length: " + length);
		
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
