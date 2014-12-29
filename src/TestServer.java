import java.net.InetAddress;
import java.util.ArrayList;


public class TestServer {

	
	public static void main(String[]args) {
		
		
		ArrayList<InetAddress> inetList = Server.getInetList();
		
		for(InetAddress i: inetList) {
			System.out.println(i);
		}
	}
}
