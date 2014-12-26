import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Server {
	private static final Logger logger = Logger.getLogger(Server.class.getName());

	private static ArrayList<InetAddress> inetList;
	
	private DatagramSocket socket;
	
	private static int serverPort = 4200;
	
	public Server() {
		
	}
	
    public void startServer() throws IOException {
    	UDPListener ul1 = new UDPListener(serverPort);
    	ul1.start();
    	UDPListener ul2 = new UDPListener(serverPort + 1);
    	ul2.start();
    	System.out.println("Created");
    }

	
	public static void setLogLevel(Level newLevel) {
		logger.setLevel(newLevel);
	}
	
	class UDPListener extends Thread {
		
		private DatagramSocket socket;
		private int serverPort;
		
		public UDPListener(int port) throws IOException {
			System.out.println("Starting udp listener");
			this.serverPort = port;

			try {
				socket = new DatagramSocket(serverPort);
			} catch (SocketException e) {
				throw new IOException("Can't create DatagramSocket: " + e.getMessage());
			}
			
			
//			synchronized (this) {
//				start();
//				try {
//					wait();
//				} catch (InterruptedException e) {
//					
//				}
//			}
		}
		public void run() {
			System.out.println("In run");
			while (true) {
				try {
					byte[] buf = new byte[10000];
					DatagramPacket packet = new DatagramPacket(buf, buf.length);
					System.out.println("Waiting for request on address "+ socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort() +" in run");
					socket.receive(packet);
					System.out.println("packet recieved");
					printRequest(packet.getData());
					processRequest(socket, packet);
				} catch (IOException e) {
					logger.warning("STUN Server: send or received failed " + e.getMessage());
				}
			}
		}
		
	}
	public void printRequest(byte[] request) {
		
		StringBuilder sb = new StringBuilder();
		sb.append("Message recieved:\n");
		
		
		int messageType = (int) (((request[0] << 8) & 0xff00) | (request[1] & 0xff));
		
		sb.append("Message type:" + messageType + "\n");
		
		System.out.println(sb.toString());
		
		
	}
	public void processRequest(DatagramSocket socket, DatagramPacket packet) {
		System.out.println("processRequest");
		byte[] request = packet.getData();
		int length = packet.getLength();
		
		InetSocketAddress isa = (InetSocketAddress) packet.getSocketAddress();

		logger.warning("Got UDP Stun request on socket "
                + socket.getLocalAddress() + ":" + socket.getLocalPort()
                + " length " + length + " bytes " + " from " + isa);

		byte[] response = getResponse(isa, request, length);
		
		InetSocketAddress responseAddress = Header.getAddress(request, Header.RESPONSE_ADDRESS);
		//System.out.println("ResponseAddress:" + responseAddress.toString());
		
		if (responseAddress != null) {

			packet.setAddress(responseAddress.getAddress());
			packet.setPort(responseAddress.getPort());
		}
		
		/*
		 * ChangeRequest - For alternating between servers in order to validate if the user
		 * is behind a Symmetric NAT. Will try implementing this. 
		 */
		
		int changeRequest = Header.getChangeRequest(request);
		
		if ((changeRequest & Header.CHANGE_IP_MASK) != 0) {
			System.out.println("Exititing because we were requested to change ip. Can't do that");
			return;
		}
		
		DatagramSocket responseSocket = socket;
		
		if ((changeRequest & Header.CHANGE_PORT_MASK) != 0) {
			try {
				responseSocket = new DatagramSocket();
				
			} catch (SocketException e) {
				e.printStackTrace();
				String s = "CHANGE_PORT set but can't create new socket! " + e.getMessage();
				response = getBindingErrorResponse(request, Header.GLOBAL_ERROR, s);
			}
			
		}
		
		packet.setData(response);
		
		try {
			responseSocket.send(packet);
			
			StringBuilder sb = new StringBuilder();
			
			if (request.length >= Header.STUN_HEADER_LENGTH + Header.TYPE_LENGTH_VALUE + Header.MAPPED_ADDRESS_LENGTH) {
				
				//All the 0xff are here to convert from signed bits to unsigned bits becasue we don't need unsigned ones here
				int port = (int) (((request[Header.STUN_HEADER_LENGTH + Header.TYPE_LENGTH_VALUE + 2] << 8) & 0xff00) | (request[Header.TYPE_LENGTH_VALUE + 3] & 0xff ));
				
				String privateAddress = "Private Address: " + (int) (request[Header.STUN_HEADER_LENGTH + Header.TYPE_LENGTH_VALUE + 4] & 0xff)
															+ (int) (request[Header.STUN_HEADER_LENGTH + Header.TYPE_LENGTH_VALUE + 5] & 0xff)
															+ (int) (request[Header.STUN_HEADER_LENGTH + Header.TYPE_LENGTH_VALUE + 6] & 0xff)
															+ (int) (request[Header.STUN_HEADER_LENGTH + Header.TYPE_LENGTH_VALUE + 7] & 0xff);
				
				sb.append(privateAddress + ":" +port);
				
				System.out.println("Sending STUN Binding Response from " + responseSocket.getLocalAddress() + ":" + responseSocket.getLocalPort() 
									+ " to " + packet.getAddress() + ":" + packet.getPort() + sb.toString());
			} 
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
	}
	
	//Den här metoden kollar om Meddelandet är långt nog för att ta ha en STUN Header (20bytes)
	//Sedan kollar den vad för typ av request som finns i headern (DET som funkar är Binding requests
	private byte[] getResponse(InetSocketAddress isa, byte[] request, int length) {
		
		//Vettigt att kolla Om paketet är större än headern på 20 byte
		if (length < Header.STUN_HEADER_LENGTH) {
			
			String msg = "Too short to have STUN HEADER" + length;
			logger.warning(msg);
			return getBindingErrorResponse(request, Header.BAD_REQUEST, msg);
		}
		
		//Hoppa mellan bitarna i meddelandet för att få ut de första bytesen
		int messageType = (int) (((request[0] << 8) & 0xff00) | (request[1] & 0xff));
		//Vi får ta och testa detta ordentligt sen^^
		
		if (messageType != Header.BINDING_REQUEST) {
			String msg = "Only Binding Request is supported";
			return getBindingErrorResponse(request, Header.GLOBAL_ERROR, msg);
		}
		return processBindingRequest(isa, request, length);
	}

	private byte[] processBindingRequest(InetSocketAddress isa, byte[] request,
			int length) {
		//Skapar datastrukturen för att kunna skicka tillbaka header, leverensaddressen samt den addressen vi ska ha
		byte[] response = new byte[Header.STUN_HEADER_LENGTH + Header.TYPE_LENGTH_VALUE + Header.MAPPED_ADDRESS_LENGTH
		                           + Header.TYPE_LENGTH_VALUE + Header.CHANGED_ADDRESS_LENGTH];
		
		System.arraycopy(request, 0, response, 0, Header.STUN_HEADER_LENGTH);
		
		response[0] = 1;	//binding response. Är detta helt korrekt??
		
		response[3] = (byte) Header.TYPE_LENGTH_VALUE + Header.MAPPED_ADDRESS +
							Header.TYPE_LENGTH_VALUE + Header.CHANGED_ADDRESS_LENGTH;
		
		response[Header.STUN_HEADER_LENGTH + 1] = Header.MAPPED_ADDRESS;	//type
		
		response[Header.STUN_HEADER_LENGTH + 3] = Header.MAPPED_ADDRESS_LENGTH;	//längden
		
		logger.fine("responding with " + isa);
		
		//Dags att ordna med addressen!
		int sourcePort = isa.getPort();
		
		response[Header.STUN_HEADER_LENGTH + 6] = (byte) (sourcePort >> 8);
		response[Header.STUN_HEADER_LENGTH + 7] = (byte) (sourcePort & 0xff);
		
		byte[] sourceAddress = isa.getAddress().getAddress();
		//PRINT ME!
		response[Header.STUN_HEADER_LENGTH + 8] = sourceAddress[0];
		response[Header.STUN_HEADER_LENGTH + 9] = sourceAddress[1];
		response[Header.STUN_HEADER_LENGTH + 10] = sourceAddress[2];
		response[Header.STUN_HEADER_LENGTH + 11] = sourceAddress[3];
		
		response[Header.STUN_HEADER_LENGTH + 13] = Header.CHANGED_ADDRESS;	//Här hoppar vi från 13 -15 why?
		
		response[Header.STUN_HEADER_LENGTH + 15] = Header.CHANGED_ADDRESS_LENGTH;
		
		response[Header.STUN_HEADER_LENGTH + 17] = 1; //Representerar addressfamiljen?!?
		
		response[Header.STUN_HEADER_LENGTH + 18] = (byte) (sourcePort >> 8);
		response[Header.STUN_HEADER_LENGTH + 19] = (byte) (sourcePort & 0xff); //bitmasking
		//bitmasking handlar om att välja ut endast de bitar som representeras i bägge argumenten och representera dem
		//Här tror jag dock att man helt enkelt tar bort signed represenationen så att det blir ett unsigned value.
		
		response[Header.STUN_HEADER_LENGTH + 20] = sourceAddress[0];
		response[Header.STUN_HEADER_LENGTH + 21] = sourceAddress[1];
		response[Header.STUN_HEADER_LENGTH + 22] = sourceAddress[2];
		response[Header.STUN_HEADER_LENGTH + 23] = sourceAddress[3];
		
		return response;
	}

	private byte[] getBindingErrorResponse(byte[] request, int responseCode,
			String reason) {
		
			byte[] response = new byte[Header.STUN_HEADER_LENGTH + Header.ERROR_CODE_LENGTH + reason.length()];
			
			System.arraycopy(request, 0, response, 0, Header.STUN_HEADER_LENGTH);
			
			response[0] = 1;
			response[1] = 0x11;
			
			response[Header.STUN_HEADER_LENGTH + 2] = (byte) (responseCode >> 8);
			
			response[Header.STUN_HEADER_LENGTH + 3] = (byte) (responseCode & 0xff);
			
			byte[] reasonBytes = reason.getBytes();
			
			System.arraycopy(reasonBytes, 0, response, 24, reasonBytes.length);
			
			int length = Header.STUN_HEADER_LENGTH + Header.ERROR_CODE_LENGTH + reasonBytes.length;
			
			response[2] = (byte) (length >> 8);
			response[3] = (byte) (length & 0xff);
			
			return response;
	}
	
	private static void fillInetList() {
		Enumeration<NetworkInterface> nets;
		try {
			nets = NetworkInterface.getNetworkInterfaces();

			for(NetworkInterface netInt: Collections.list(nets)) {
				for(InetAddress iAdd: Collections.list(netInt.getInetAddresses())) {
					inetList.add(iAdd);
					System.out.println(iAdd.getHostAddress() + " added to inetList");

				}
			}
		}
		catch(SocketException e) { System.out.println("Socket exception while adding local network addresses to list " + e.getMessage()); }
	}
	
	public static void main(String[]args) {
		logger.fine("asd");
		logger.toString();
		
		inetList = new ArrayList<InetAddress>();
		fillInetList();
		
		Server server = new Server();
		
		try {
			server.startServer();
		} catch (IOException e) {
			System.out.println("IOException " + e.getMessage());
			System.exit(1);
		}
		System.out.println("Server started");
		/*try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		server.test();
		*/
	}
	
	public void test() {
		System.out.println("Starting test");
		DatagramSocket socket = null;
		
		try { 
			socket = new DatagramSocket();
		} catch (SocketException e) {
			System.out.println(e.getMessage());
			System.exit(1);
		}
		
		byte[] request = new byte[Header.STUN_HEADER_LENGTH];
		
		request[1] = 1;
		
		for (int i = 0; i < 16; i++) {
			request[4 + i] = (byte) i;
		}
		DatagramPacket packet = null;
		
		try {

			packet = new DatagramPacket(request, request.length, InetAddress.getLocalHost(), serverPort);
		} catch (UnknownHostException e) {
			System.out.println("Can't get localhost");
			System.exit(1);
		}
		
		try {
			socket.send(packet);
			System.out.println("packet sent");
			if (logger.isLoggable(Level.FINEST)) {
				Header.dump("Sent stun binding request to " + packet.getAddress() + ": " + packet.getPort(), request, 0, request.length);
			}
		} catch (IOException e) {
			System.out.println("Unable to send STUN binding request! " + e.getMessage());
		}
	}

}
