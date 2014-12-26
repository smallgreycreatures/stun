import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;


public class Test {
	
	private ArrayList<InetAddress> inetList;
	private DatagramSocket socket;
	private InetSocketAddress serverAddress;
	
	public static void main(String[]args) {
		Test t = new Test();
		
		t.process();
		
		
	}
	
	private void fillInetList() {
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
	
	public void process() {
		inetList = new ArrayList<InetAddress>();
		fillInetList();
		try {
			socket = new DatagramSocket(4202);
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		serverAddress = new InetSocketAddress(inetList.get(1), 4200);
		try {
			Client client = new Client(serverAddress, socket);
			InetSocketAddress address = client.getMappedAddress();
			System.out.println(address.toString());
		} catch (IOException e) {

			e.printStackTrace();
		}
		
		
	}
}
