/**
 * The class is to set default server information and can be configurable in terminal.
 * @author Sheng Wu
 * @version 1.0 29/04/2017
 */
package EZShare;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

public class ServerInfo {
	public static int connectionInterval = 1;  //sec
	public static int exchangeInterval = 600;  //sec
	public static int timeout = 300;  //sec
	public static String secret = UUID.randomUUID().toString();
	public static String hostName = "localhost";
	public static int port = 3000;
	public static boolean debug = false; 
	public static int sport = 3781;
	/*
	static {
		try {
			hostName = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}*/
}
