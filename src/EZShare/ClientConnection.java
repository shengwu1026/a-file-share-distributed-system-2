/**
 * This class in responsible for open the client socket and establish a connection with the server.
 * Once the connection is done, close the socket.
 * @author Sheng Wu
 * @version 1.0 29/04/2017
 */
package EZShare; 

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.apache.log4j.Logger;

public class ClientConnection { 
	private static Logger logger = Logger.getLogger(ClientConnection.class);
	
	/**
	 * The method is to establish a non-persistent connection with a specific server. Send the message and
	 * receive the messages from the server and return them. 
	 * @param serverBean an object with attributes: hostname, address, port
	 * @param message a json string describing what the user enters in terminal
	 * @param secure whether the connection is secure or not
	 * @return messages a list of messages from the server
	 */ 
	public static List<Message> establishConnection(ServerBean serverBean, Message message, boolean secure) {
		Socket socket = null;
		Message response = null;
		List<Message> messages = new ArrayList<>();
		
		try {
			if(secure) {
				SSLContext context = null;
				try {
					context = SSLContext.getDefault();
				} catch (NoSuchAlgorithmException e) { 
					e.printStackTrace();
				}
				SSLSocketFactory sslsocketfactory = (SSLSocketFactory) context.getSocketFactory(); 
				socket = (SSLSocket) sslsocketfactory.createSocket(serverBean.getAddress(), serverBean.getPort());
			} else {
				socket = new Socket(serverBean.getAddress(), serverBean.getPort());
			}	
			 
			socket.setSoTimeout(ServerInfo.timeout * 1000);
			DataInputStream inputStream = new DataInputStream(socket.getInputStream());
			DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
			outputStream.writeUTF(message.getMessage());
			outputStream.flush();
			
			logger.info("SENT: " + (secure ? "(secure) " : "(insecure) ") + message.getMessage());
			String data = null; 
			try { 
				while((data = inputStream.readUTF())!= null) { 
					logger.info("RECEIVED: " + (secure ? "(secure) " : "(insecure) ") + data);
					response = new Message(MessageType.STRING, data, null, null);
					messages.add(response);
				} 
			} catch(EOFException e) {
				logger.debug("All message has been received");
			}			
		} catch (IOException e) {
			e.printStackTrace();
			logger.debug("Lost connection to: " + (secure ? "(secure) " : "(insecure) ") + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
		} finally { 
			if (socket != null) {
				try {
					socket.close();
					logger.debug("Close connection to: " + (secure ? "(secure) " : "(insecure) ") + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
				} catch (IOException e) { 
					e.printStackTrace();
				}
			}
			return messages;
		}  
	}
	
	public static void establishPersistentConnection(ServerBean serverBean, Message message, KeyboardListener keyboardListener, MessageListener messageListener, boolean secure) {
		try {
			Socket socket = null;
			if (secure) { 
				SSLSocketFactory sslsocketfactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
				socket = (SSLSocket) sslsocketfactory.createSocket(serverBean.getAddress(), serverBean.getPort());
			} else {
				socket = new Socket(serverBean.getAddress(),serverBean.getPort());
			}
			
			BufferedReader sysReader = new BufferedReader(new InputStreamReader(System.in));
			DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
			DataInputStream inputStream = new DataInputStream(socket.getInputStream());
			outputStream.writeUTF(message.getMessage());
			outputStream.flush();
			
			new Thread(new Runnable() {
				@Override
				public void run() {
					String string = null;
					try {
						while((string = inputStream.readUTF()) != null) {
							Message response = new Message(string);
							if(messageListener.onMessageReceived(response)) break;
						}
					} catch (IOException e) {
						//e.printStackTrace();
					}
				}
			}).start();
			 
			new Thread(new Runnable() {
				@Override
				public void run() {
					String string = null;
					try {
						while((string = sysReader.readLine()) != null) {
							if(keyboardListener.onKeyPressed(outputStream,string)) break;
						}
					} catch (IOException e) {
						//e.printStackTrace();
					}
				}
			}).start();
		} catch (IOException e) {
			//e.printStackTrace();
		}
	}
	
	interface KeyboardListener {
		boolean onKeyPressed(DataOutputStream outputStream, String string);
	}
	
	interface MessageListener {
		boolean onMessageReceived(Message message);
	}
	
}