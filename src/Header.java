import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class that implements the operations on the STUN message header.
 * Everything that is represented with the keyword MUST is a requirement
 * from RFC5389.
 * @author Frans
 *
 */
public class Header {

	private static final Logger logger = Logger.getLogger(Header.class.getName());
	private static ConsoleHandler consoleHandler = new ConsoleHandler();

	public static final int LENGTH = 20;

	public static final int TYPE_LENGTH_VALUE = 4;

	public static final int ERROR_CODE_LENGTH = 4;

	public static final int BINDING_REQUEST = 1;
	public static final int BINDING_RESPONSE = 0x101;

	public static final int MAPPED_ADDRESS = 1;
	public static final int MAPPED_IPV4_ADDRESS_LENGTH = 8;
	public static final int MAPPED_IPV6_ADDRESS_LENGTH = 20;


	public static final int RESPONSE_ADDRESS = 2;
	public static final int RESPONSE_ADDRESS_LENGTH = 8;

	public static final int CHANGE_REQUEST = 3;
	public static final int CHANGE_REQUEST_LENGTH = 4;
	public static final int CHANGE_PORT_MASK = 2;
	public static final int CHANGE_IP_MASK = 4;

	public static final int CHANGED_ADDRESS = 5;
	public static final int CHANGED_ADDRESS_LENGTH = 8;

	public static final int BAD_REQUEST = 400;
	public static final int GLOBAL_ERROR = 600;

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
	 * A STUN Header MUST contain Type and Length of the request
	 * @param request A STUN request
	 */
	public static void addTypeAndLengthTo(byte[] request) {
		request[1] = (byte) BINDING_REQUEST;
		request[3] = (byte) TYPE_LENGTH_VALUE + MAPPED_IPV4_ADDRESS_LENGTH;
	}

	/**
	 * A STUN Header MUST contain a Magic cookie with the value of 0x2112A442
	 * according to RFC3489.
	 * @param request A STUN request
	 */
	public static void addMagicCookieTo(byte[] request) {
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
	 * @param request A STUN request
	 */
	public static void addTransactionIDTo(byte[] request) {
		SecureRandom rnd = new SecureRandom();
		byte rndBytes[] = new byte[12];
		rnd.nextBytes(rndBytes);

		for (int i = 0; i < 12; i++) {
			request[i+8] = rndBytes[0];
		}
	}
	
	/**
	 * Takes a byte array and see if it has a STUN Magic Cookie at the
	 * right place.
	 * @param request A STUN request
	 * @return true if magic cookie is there
	 */
	public static boolean compareMagicCookieIn(byte[] request) {

		int magicCookie = 0x2112A442; //0x2112A442 = 10x554869826
		int extractedCookie = (int) ((request[4] << 24 & 0xff000000) | (request[5] << 16 & 0xff0000) 
				| (request[6] << 8 & 0xff00) | (request[7] & 0xff));

		logger.log(Level.FINE, "magic cookie = incoming cookie "+ magicCookie + " = " + extractedCookie);

		return (magicCookie == extractedCookie) ? true : false;
	}
	
	/**
	 * Method that checks that the STUN attributes are there and then retrieves the
	 * mapped address. It is used by Clients to discover their IP.
	 * 
	 * It checks if it's an IPv4 or IPv6 address, extracts the address and the port 
	 * and returns it as an InetSocketAddress
	 * @param request A STUN request
	 * @param desiredType What we want. Mapped address usually.
	 * @return Global IP address and Global Port number
	 */
	public static InetSocketAddress getAddress(byte[] request, int desiredType) {

		InetSocketAddress isa = null;

		int length = (int) (((request[2] << 8) & 0xff00) | (request[3] & 0xff));
		logger.log(Level.FINE, "Length in Header: " + length);
		int offset = LENGTH;

		logger.log(Level.FINER, "Searching for type " + Integer.toHexString(desiredType));

		while (length > 0) {
			int type = (int) request[offset +1];
			logger.log(Level.FINE, "Type: " + type + "desiredType: " + desiredType);

			int attributeLength = (int) (((request[offset + 2] << 8) & 0xff00) | (request[offset + 3] & 0xff));
			logger.log(Level.FINE, "Attribute length in Header:" + attributeLength);

			if (type != desiredType) {
				logger.log(Level.FINE, "Skipping type " + type);

				offset += (TYPE_LENGTH_VALUE + attributeLength);
				length -= (TYPE_LENGTH_VALUE + attributeLength);
				continue;
			}

			if (attributeLength != MAPPED_IPV4_ADDRESS_LENGTH && attributeLength != MAPPED_IPV6_ADDRESS_LENGTH) {
				logger.log(Level.WARNING, "Invalid Response Address Length");
				return null;
			}

			int port = (int) (((request[offset + 6] << 8) & 0xff00) | (request[offset + 7] & 0xff));
			logger.log(Level.FINE, "Port in Header: " + port);
			InetAddress inetAddress;

			try {
				byte [] address = new byte[4];
				
				for (int i = 0; i < address.length; i++) {
					address[i] = request[offset + 8 + i];
				}

				inetAddress = InetAddress.getByAddress(address);

			} catch (UnknownHostException e) {
				logger.warning("Invalid Response Address: " + e.getMessage());
				return null;
			}

			isa = new InetSocketAddress(inetAddress, port);
			logger.log(Level.FINE, "Found Address " + isa);
			break;
		}
		logger.log(Level.FINE, "Got address");
		return isa;
	}

	/**
	 * Method that extracts the Change request from the STUN header.
	 * ChangeRequests are for alternating between servers in order to validate if the user is 
	 * behind a symmetric NAT. Have not implemented that part but it gets the change request
	 * and tries to change port.
	 * 
	 * @param request A STUN request
	 * @return The STUN value of a change request
	 */
	public static int getChangeRequest(byte[] request) {

		int changeRequest = 0;

		int length = (int) (((request[2] << 8) & 0xff00) | (request[3] & 0xff));

		int offset = LENGTH;

		logger.log(Level.FINE ,"Searching for change request attribute");

		while (length > 0) {

			int type = (int) request[offset + 1];

			int attributeLength = (int) (((request[offset + 2] << 8) & 0xff00)) | (request[offset + 3] & 0xff);

			if (type != CHANGE_REQUEST) {
				logger.log(Level.FINE, "Skipping type " + type);
				offset += (TYPE_LENGTH_VALUE + attributeLength);
				length -= (TYPE_LENGTH_VALUE + attributeLength);
				continue;
			}

			if (attributeLength != CHANGE_REQUEST_LENGTH) {
				logger.log(Level.WARNING, "Invalid Change Request Length " + attributeLength);
				return 0;
			}
			changeRequest = (int) request[offset + 7];
			logger.log(Level.FINE, "Found change request " + changeRequest);
			break;
		}
		return changeRequest;
	}
}
