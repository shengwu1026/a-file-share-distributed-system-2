/**
 * This class is for start a socket for server and create a thread pool for execution.
 * Server socket doesn't close in a normal situation.
 * @author Sheng Wu
 * @version 1.0 29/04/2017
 */

package EZShare;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException; 
import java.net.ServerSocket;
import java.net.Socket; 
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
 
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;  
import org.apache.log4j.Logger; 

public class ServerConnection {
	Logger logger = Logger.getLogger(ServerConnection.class);
	
	private ThreadPoolExecutor executor;
	private ThreadPoolExecutor persistentExecutor;
	private Map<String,Long> connectionIntevalInfo; 
	
	public ServerConnection() {
		executor = new ThreadPoolExecutor(50, 50, ServerInfo.timeout, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		persistentExecutor = new ThreadPoolExecutor(50, 50, ServerInfo.timeout, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		connectionIntevalInfo = new ConcurrentHashMap<>();
	}
	
	/**
	 * The method handles connection from the client. The server will ensure the time between successive 
	 * connections from any IP addresss be no less than a limit (1 sec by default). If satisfies the condition,
	 * the server puts the thread to the thread pool.
	 * @param serverBean
	 */
	public void handleConnection(ServerBean serverBean) {
		try {
			ServerSocket serverSocket = new ServerSocket(serverBean.getPort());
			while (true) {
				Socket clientSocket = serverSocket.accept();
				clientSocket.setSoTimeout(ServerInfo.timeout * 1000);
				String ipAddress = clientSocket.getInetAddress().getHostAddress();
				logger.debug("Insecurely connected to: " + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());
				if (!checkConnectionInterval(ipAddress)){    // violate connection interval
					clientSocket.close();
					continue;
				}
				executor.execute(new Communication(clientSocket, false, false));     // persistent, secure
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void handleSecureConnection(ServerBean serverBean) {
		try { 
			SSLContext context = null;
			try {
				context = SSLContext.getDefault();
			} catch (NoSuchAlgorithmException e) { 
				e.printStackTrace();
			}
			SSLServerSocketFactory sslserversocketfactory = (SSLServerSocketFactory) context.getServerSocketFactory();
			SSLServerSocket sslserversocket = (SSLServerSocket) sslserversocketfactory.createServerSocket(ServerInfo.sport);
			while (true) { 
				SSLSocket sslClientSocket = (SSLSocket) sslserversocket.accept();
				sslClientSocket.setSoTimeout(ServerInfo.timeout * 1000);
				String ipAddress = sslClientSocket.getInetAddress().getHostAddress();
				logger.debug("Securely connected to: " + sslClientSocket.getInetAddress().getHostAddress() + ":" + sslClientSocket.getPort());
				if (!checkConnectionInterval(ipAddress)) {        // violate connection interval
					sslClientSocket.close();
					continue;
				}
				executor.execute(new Communication(sslClientSocket, false, true));    // persistent, secure
			}
		} catch (IOException e) { 
			e.printStackTrace();
		}
	}
	
	// true -- execute connection
	// false -- abort
	private boolean checkConnectionInterval(String ipAddress){
		if (!connectionIntevalInfo.containsKey(ipAddress)) {
			connectionIntevalInfo.put(ipAddress, System.currentTimeMillis());
			return true;
		} else {
			if (System.currentTimeMillis() - connectionIntevalInfo.get(ipAddress) < ServerInfo.connectionInterval * 1000) {
				logger.error("The client: " + ipAddress + " violates connection interval.");
				return false;
			} else {
				return true;
			}
		}
		
	}
	
	/**
	 * The method is to establish a no-persistent connection with a specific server. Send the message and
	 * receive the messages from the server and return them.
	 * @param serverBean an object with attributes: hostname, address, port
	 * @param message a json string describing what the user enters in terminal
	 * @return messages a list of messages from the server
	 */
	public List<Message> establishConnection(ServerBean serverBean, Message message, boolean secure) {
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
			while ((data = inputStream.readUTF()) != null) { 
				logger.info("RECEIVED: " + (secure ? "(secure) " : "(insecure) ") + data);
				response = new Message(MessageType.STRING, data, null, null);
				messages.add(response);
			} 
		} catch (IOException e) {
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
	 
	public void establishPersistentConnection(ServerBean serverBean, Message message, MessageListener messageListener, StateListener stateListener, boolean secure) {
		Socket socket = null;
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
			DataInputStream inputStream = new DataInputStream(socket.getInputStream());
			DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
			outputStream.writeUTF(message.getMessage());
			outputStream.flush();
			
			logger.info("SENT: " + (secure ? "(secure) " : "(insecure) ") + "(persistent) " + message.getMessage());
			
			// The thread listens for incoming message. Stops when it receives one message.
			Thread listeningThread = new Thread(new Runnable() {
				@Override
				public void run() {
					String data = null;
					try {
						while((data = inputStream.readUTF()) != null) {
							Message response = new Message(data);
							logger.info("RECEIVED: " + (secure ? "(secure) " : "(insecure) ") + "(persistent) " + response.getMessage());
							if(messageListener.onMessageReceived(response, outputStream)) break;
						}
						//logger.debug("Close connection:" + (secure ? "(secure) " : "(insecure) ") + socket.getInetAddress() + socket.getPort());
					} catch(IOException e) {
						e.printStackTrace();
					}
				}
			});

			listeningThread.start();

			while(true) {
				if(stateListener.onForceStop(outputStream) || socket.isClosed()) {
					socket.close();
					break;
				}
			}
		} catch(IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if(socket != null) {
					socket.close();
				} 
			} catch (IOException e) { 
				e.printStackTrace();				
			}
		}
	}

	interface MessageListener {
		boolean onMessageReceived(Message message, DataOutputStream outputStream);
	}
	
	interface StateListener {
		boolean onForceStop(DataOutputStream outputStream);
	}
}












