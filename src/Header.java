import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Header {

	private static final Logger logger = Logger.getLogger(Header.class.getName());
	private static ConsoleHandler consoleHandler = new ConsoleHandler();

	public static final int LENGTH = 20;

	public static final int TYPE_LENGTH_VALUE = 4;

	public static final int ERROR_CODE_LENGTH = 4;

	public static final int BINDING_REQUEST = 1;
	public static final int BINDING_RESPONSE = 0x101;

	public static final int MAPPED_ADDRESS = 1;
	public static final int MAPPED_ADDRESS_LENGTH = 8;

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

	public static void connectConsoleHandler() {
		logger.addHandler(consoleHandler);
	}

	public static void setLogLevel(Level newLevel) {
		logger.setLevel(newLevel);
	}

	public static void setConsoleHandlerLevel(Level newLevel) {
		consoleHandler.setLevel(newLevel);
	}

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
				logger.finest("Skipping type " + type);

				offset += (TYPE_LENGTH_VALUE + attributeLength);
				length -= (TYPE_LENGTH_VALUE + attributeLength);
				continue;
			}

			if (attributeLength != MAPPED_ADDRESS_LENGTH) {
				logger.warning("Invalid Response Address Length");
				return null;
			}

			int port = (int) (((request[offset + 6] << 8) & 0xff00) | (request[offset + 7] & 0xff));
			logger.log(Level.FINE, "Port in Header: " + port);
			InetAddress ia;

			try {
				byte [] address = new byte[4];

				address[0] = request[offset + 8];
				address[1] = request[offset + 9];
				address[2] = request[offset + 10];
				address[3] = request[offset + 11];

				ia = InetAddress.getByAddress(address);

			} catch (UnknownHostException e) {
				logger.warning("Invalid Response Address: " + e.getMessage());
				return null;
			}

			isa = new InetSocketAddress(ia, port);
			logger.log(Level.FINE, "Found Address " + isa);
			break;
		}
		logger.log(Level.FINE, "Got address");
		return isa;
	}

	public static int getChangeRequest(byte[] request) {

		int changeRequest = 0;

		int length = (int) (((request[2] << 8) & 0xff00) | (request[3] & 0xff));

		int offset = LENGTH;

		logger.finest("Searching for change request attribute");

		while (length > 0) {

			int type = (int) request[offset + 1];

			int attributeLength = (int) (((request[offset + 2] << 8) & 0xff00)) | (request[offset + 3] & 0xff);

			if (type != CHANGE_REQUEST) {
				logger.fine("Skipping type " + type);
				offset += (TYPE_LENGTH_VALUE + attributeLength);
				length -= (TYPE_LENGTH_VALUE + attributeLength);
				continue;
			}

			if (attributeLength != CHANGE_REQUEST_LENGTH) {
				logger.warning("Invalid Change Request Length " + attributeLength);
				return 0;
			}
			changeRequest = (int) request[offset + 7];
			logger.finest("Found change request " + changeRequest);
			break;
		}
		return changeRequest;
	}

}
