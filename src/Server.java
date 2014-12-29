import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Server {
	private static Logger logger = Logger.getLogger(Server.class.getName());
	private static ConsoleHandler consoleHandler = new ConsoleHandler();

	private static int serverPort = 3478;
	private static InetAddress serverAddress;

	private int nrOfThreads;
	private ExecutorService executorService;

	private UDPListener[] udpListeners;
	private TCPListener[] tcpListeners;

	/**
	 * Empty constructor if you want to use default InetAddress and STUN port 3478
	 */
	public Server() {

	}

	/**
	 * Constructor if you want to specify which network interface to use and which port
	 * Port number 3478 is Strongly Recommended!
	 * @param myAddress
	 * @param myPort
	 */
	public Server(InetAddress myAddress, int myPort) {
		this.serverPort = myPort;
		this.serverAddress = myAddress;
	}

	public void startServer() throws IOException {
		this.nrOfThreads = nrOfThreads();
		executorService = Executors.newFixedThreadPool(nrOfThreads);

		udpListeners = new UDPListener[nrOfThreads];
		tcpListeners = new TCPListener[nrOfThreads];
		
		for (int i = 0; i < nrOfThreads; i++) {
			
			if ((i % 2) == 0) {
				if (serverAddress != null)
					udpListeners[i] = new UDPListener(serverPort+i, serverAddress);
				else
					udpListeners[i] = new UDPListener(serverPort+i);
				
			} else {
				if (serverAddress != null) 
					tcpListeners[i] = new TCPListener(serverPort+i, serverAddress);
				else	
					tcpListeners[i] = new TCPListener(serverPort+i);
				
			}
		}
		
		for (int i = 0; i < nrOfThreads; i++) {
			
			if ((i % 2) == 0) {
				executorService.execute(udpListeners[i]);
			} else {
				executorService.execute(tcpListeners[i]);
			}
		}
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

	/**
	 * Fills an ArrayList with the available network interfaces and returns it
	 * @return ArrayList with available network interfaces
	 */
	public static ArrayList<InetAddress> getInetList() {
		return fillInetList();
	}

	private static ArrayList<InetAddress> fillInetList() {
		ArrayList<InetAddress> inetList = new ArrayList<InetAddress>();

		Enumeration<NetworkInterface> nets;
		try {
			nets = NetworkInterface.getNetworkInterfaces();

			for(NetworkInterface netInt: Collections.list(nets)) {
				for(InetAddress iAdd: Collections.list(netInt.getInetAddresses())) {
					inetList.add(iAdd);
					logger.log(Level.FINE, iAdd.getHostAddress() + " added to inetList");

				}
			}
		} catch (SocketException e) { 
			logger.log(Level.WARNING, "Socket exception while adding local network addresses to list " + e.getMessage()); 
		}
		return inetList;

	}

	/**
	 * Method that decides the number of threads that the server should use
	 * @return > 4
	 */
	private int nrOfThreads() {
		int availableCPUS = Runtime.getRuntime().availableProcessors();

		return (availableCPUS > 4) ? availableCPUS : 4;
	}

	class TCPListener implements Runnable {
		private ServerSocket serverSocket;
		private int serverPort;
		private InetAddress serverAddress;

		public TCPListener(int port) throws IOException {
			logger.log(Level.FINE, "Starting ServerSocket listener nr "+ ((port - 3478)/2) + " on port " + port);
			this.serverPort = port;

			try {
				serverSocket = new ServerSocket(serverPort);

			} catch (SocketException e) {
				throw new IOException("Can't create ServerSocket: " + e.getMessage());
			}

		}

		/**
		 * Alternative constructor if you want to specify which of your inet address you want to use
		 * @param port
		 * @param serverAddress
		 * @throws IOException
		 */
		public TCPListener(int port, InetAddress serverAddress) throws IOException {
			logger.log(Level.FINE, "Starting TCP listener nr " + ((port - 3478)/2) + "on address " + serverAddress + ":" + port);
			this.serverPort = port;
			this.serverAddress = serverAddress;

			try {
				serverSocket = new ServerSocket(serverPort, 50, serverAddress);

			} catch (SocketException e) {
				throw new IOException("Can't create ServerSocket: " + e.getMessage());
			}
		}

		public void run() {
			boolean running = true;

			while (running) {
				try {
					logger.log(Level.FINE, "Waiting for requests on address "+ serverSocket.getInetAddress() + ":" + serverSocket.getLocalPort() +" in run");

					Socket socket = serverSocket.accept();
					logger.log(Level.FINE, "Connection recieved");

					processRequest(socket);

				} catch (IOException e) {
					running = false;
					logger.log(Level.FINE, "IOException for ServerSocket - " + e.getMessage());
				}
			}
		}
	}

	class UDPListener implements Runnable {

		private DatagramSocket socket;
		private int serverPort;
		private InetAddress serverAddress;

		public UDPListener(int port) throws IOException {
			logger.log(Level.FINE, "Starting UDP listener nr "+ ((port - 3478)/2) + " on port " + port);
			this.serverPort = port;

			try {
				socket = new DatagramSocket(serverPort);

			} catch (SocketException e) {
				throw new IOException("Can't create DatagramSocket: " + e.getMessage());
			}

		}

		/**
		 * Alternative constructor if you want to specify which of your inet address you want to use
		 * @param port
		 * @param serverAddress
		 * @throws IOException
		 */
		public UDPListener(int port, InetAddress serverAddress) throws IOException {
			logger.log(Level.FINE, "Starting UDP listener nr " + ((port - 3478)/2) + "on address " + serverAddress + ":" + port);
			this.serverPort = port;
			this.serverAddress = serverAddress;

			try {

				socket = new DatagramSocket(serverPort, serverAddress);
			} catch (SocketException e) {
				throw new IOException("Can't create DatagramSocket: " + e.getMessage());
			}
		}

		public void run() {
			boolean running = true;
			while (running) {
				try {
					byte[] buffer = new byte[1024];
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					logger.log(Level.FINE, "Waiting for requests on address "+ socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort() +" in run");

					socket.receive(packet);
					logger.log(Level.FINE, "Packet recieved.");

					processRequest(socket, packet);
				} catch (IOException e) {
					running = false;
					logger.log(Level.FINE, "IOException for UDP Socket - " + e.getMessage());
				}
			}
			logger.log(Level.FINE,"Thread " + (serverPort - 3478) + " out of running");
		}
	}
	public void printResponse(byte[] request) {

		StringBuilder sb = new StringBuilder();
		sb.append("Message recieved:\n");


		int messageType = (int) (((request[0] << 8) & 0xff00) | (request[1] & 0xff));
		int length = request.length;

		sb.append("Message type:" + messageType + "\n"
				+ "Packet Lenght: " + length + "\n");


		//System.out.println(sb.toString());


	}

	public void processRequest(DatagramSocket socket, DatagramPacket packet) {
		logger.log(Level.FINE, "Processing request.");
		byte[] request = packet.getData();
		int length = packet.getLength();

		if (checkMagicCookieFor(request)) {

			InetSocketAddress isa = (InetSocketAddress) packet.getSocketAddress();

			logger.log(Level.FINE, "Got UDP Stun request on socket "
					+ socket.getLocalAddress() + ":" + socket.getLocalPort()
					+ " length " + length + " bytes " + " from " + isa);

			byte[] response = buildResponse(isa, request, length);
			printResponse(response);

			/*
			 * ChangeRequest - For alternating between servers in order to validate if the user
			 * is behind a Symmetric NAT. Will try implementing this. 
			 */
			int changeRequest = Header.getChangeRequest(request);

			if (!changeIP(changeRequest)) {

				DatagramSocket responseSocket = setSocket(socket, changeRequest, request, response);
				packAndSendData(responseSocket, packet, response);

			}
		} else {
			logger.log(Level.FINE, "magic cookie not ok, Probably not a STUN request. Not much to do");
		}
	}

	public void processRequest(Socket socket) throws IOException {
		InputStream input = socket.getInputStream();
		OutputStream output = socket.getOutputStream();

		byte[] request = streamToByteArray(input);

		if (checkMagicCookieFor(request)) {

			InetSocketAddress isa = new InetSocketAddress(socket.getInetAddress(), socket.getPort());
			logger.log(Level.FINE, "Message received from " + isa);

			byte[] response = buildResponse(isa, request, request.length);

			output.write(response);
			logger.log(Level.FINE, "Message sent to " + isa);
		}

	}

	private byte[] streamToByteArray(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		byte[] data = new byte[1024];
		int read;

		while ((read = is.read(data, 0, data.length)) != -1) {
			buffer.write(data, 0, read);
		}

		buffer.flush();

		return buffer.toByteArray();
	}

	private boolean checkMagicCookieFor(byte[] request) {

		int magicCookie = 0x2112A442; //0x2112A442 = 10x554869826
		int extractedCookie = (int) ((request[4] << 24 & 0xff000000) | (request[5] << 16 & 0xff0000) 
				| (request[6] << 8 & 0xff00) | (request[7] & 0xff));

		logger.log(Level.FINE, "magic cookie = incoming cookie "+ magicCookie + " = " + extractedCookie);

		return (magicCookie == extractedCookie) ? true : false;
	}

	private boolean changeIP(int changeRequest) {

		if ((changeRequest & Header.CHANGE_IP_MASK) != 0) {

			logger.warning("Exit because we were requested to change ip. Can't do that (yet)");
			return true;

		} else {

			return false;
		}
	}

	private DatagramSocket setSocket(DatagramSocket socket, int changeRequest, byte[] request, byte[] response) {

		if ((changeRequest & Header.CHANGE_PORT_MASK) != 0) {
			try {
				DatagramSocket responseSocket = new DatagramSocket();
				return responseSocket;

			} catch (SocketException e) {
				e.printStackTrace();
				String s = "CHANGE_PORT set but can't create new socket! " + e.getMessage();
				response = buildErrorResponse(request, Header.GLOBAL_ERROR, s);
			}
		}
		return socket;
	}

	private void packAndSendData(DatagramSocket socket, DatagramPacket packet, byte[] response) {
		try {
			packet.setData(response);
			socket.send(packet);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * checks if the STUN Header is okay
	 * @param request
	 * @param length
	 * @return 1 if okay, otherwise STUN error code
	 */
	private int checkHeaderErrors(byte[] request, int length) {

		if (length < Header.LENGTH) {

			return Header.BAD_REQUEST;
		}
		int messageType = (int) (((request[0] << 8) & 0xff00) | (request[1] & 0xff));

		if (messageType != Header.BINDING_REQUEST) {

			return Header.GLOBAL_ERROR;
		}

		return 1;
	}

	private byte[] buildResponse(InetSocketAddress isa, byte[] request, int length) {

		int messageType = checkHeaderErrors(request, length);

		if (messageType == 1) {
			return buildBindingResponse(isa, request);
		} else {
			if (messageType == 400) 
				return buildErrorResponse(request, messageType, "BAD REQUEST - Header to small");
			else 
				return buildErrorResponse(request, messageType, "GLOBAL ERROR - Only Binding Requests accepted");
		}
	}

	private byte[] buildBindingResponse(InetSocketAddress isa, byte[] request) {
		logger.log(Level.FINE, "Building Binding Response");

		int ipFamily = ipFamilyChecker(isa);
		int mappedAttributeLength = ((ipFamily == 2)? Header.MAPPED_IPV6_ADDRESS_LENGTH: Header.MAPPED_IPV4_ADDRESS_LENGTH);
		byte[] response = new byte[Header.LENGTH + Header.TYPE_LENGTH_VALUE + mappedAttributeLength];

		System.arraycopy(request, 0, response, 0, Header.LENGTH);

		setHeaderTo(response, mappedAttributeLength);
		setMappedTypeAndValueTo(response, mappedAttributeLength, ipFamily);

		logger.log(Level.FINE, "responding with " + isa);

		addAttributesTo(response, isa, ipFamily);
		addSourceAddressTo(response, isa);

		return response;
	}

	/**
	 * Method to determine if the address is an instance of IPv4 or IPv6
	 * @param isa
	 * @return 2 if isa == IPv6, 1 if isa == IPv4 
	 */
	private int ipFamilyChecker(InetSocketAddress isa) {
		InetAddress ia = isa.getAddress();

		return (ia instanceof Inet6Address) ? 2 : 1;
	}


	private void setHeaderTo(byte[] response, int attributeLength) {
		response[0] = 1;
		response[3] = (byte) (Header.TYPE_LENGTH_VALUE + attributeLength);
	}

	private void setMappedTypeAndValueTo(byte[] response, int attributeLength, int ipFamily) {
		response[Header.LENGTH + 1] = Header.MAPPED_ADDRESS;

		response[Header.LENGTH + 3] = (byte) attributeLength;

	}

	private void addAttributesTo(byte[] response, InetSocketAddress isa, int ipFamily) {
		int sourcePort = isa.getPort();

		response[Header.LENGTH + 5] = (byte) ipFamily;

		response[Header.LENGTH + 6] = (byte) (sourcePort >> 8);
		response[Header.LENGTH + 7] = (byte) (sourcePort & 0xff);

	}

	private void addSourceAddressTo(byte[] response, InetSocketAddress isa) {
		byte[] sourceAddress = isa.getAddress().getAddress();

		for (int i = 0; i < sourceAddress.length; i++) {
			response[Header.LENGTH + 8 + i] = sourceAddress[i];
		}
	}

	private byte[] buildErrorResponse(byte[] request, int responseCode, String reason) {

		byte[] response = new byte[Header.LENGTH + Header.ERROR_CODE_LENGTH + reason.length()];

		System.arraycopy(request, 0, response, 0, Header.LENGTH);

		response[0] = 1;
		response[1] = 0x11;

		response[Header.LENGTH + 2] = (byte) (responseCode >> 8);

		response[Header.LENGTH + 3] = (byte) (responseCode & 0xff);

		byte[] reasonBytes = reason.getBytes();

		System.arraycopy(reasonBytes, 0, response, 24, reasonBytes.length);

		int length = Header.LENGTH + Header.ERROR_CODE_LENGTH + reasonBytes.length;

		response[2] = (byte) (length >> 8);
		response[3] = (byte) (length & 0xff);

		return response;
	}

	public void shutdown() throws IOException {
		logger.log(Level.FINE, "Shutting down thread pool.");

		if (executorService != null) {
			executorService.shutdown();

			for (int i = 0; i < nrOfThreads; i++) {
				
				if ((i % 2 ) == 0) 
					udpListeners[i].socket.close();
				
				else
					tcpListeners[i].serverSocket.close();
			}
		}

	}

	public static void main(String[]args) {


		Server server = new Server();

		try {
			server.startServer();
		} catch (IOException e) {
			System.out.println("IOException " + e.getMessage());
			System.exit(1);
		}
		logger.log(Level.INFO, "Server started");
	}
}
