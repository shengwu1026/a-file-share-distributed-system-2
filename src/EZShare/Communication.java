/**
 * This class is for the server and client to send and receive messages. 
 * @author Sheng Wu
 * @version 1.0 29/04/2017
 */

package EZShare;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket; 
import java.util.List; 
import org.apache.log4j.Logger; 

public class Communication implements Runnable {
	private Socket clientSocket;
	private DataInputStream inputStream;
	private DataOutputStream outputStream;
	private ServerCommandProcessor processor;
	private boolean secure;
	private boolean persistent;
	
	Logger logger = Logger.getLogger(Communication.class);

	/**
	 * The method is a construction method.
	 * @param clientSocket
	 */
	public Communication(Socket clientSocket, boolean persistent, boolean secure) {
		this.clientSocket = clientSocket;
		this.processor = ServerCommandProcessor.getInstance();
		this.secure = secure;
		this.persistent = persistent;
		
		try {
			this.inputStream = new DataInputStream(clientSocket.getInputStream());
			this.outputStream = new DataOutputStream(clientSocket.getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * The method runs after Communication() is being called and will read the message
	 * the client sends and send messages that server respond. 
	 */
	public void run() {
		try {
			String commandStr = inputStream.readUTF();  //read client input command (publish, etc.)
			
			logger.info("RECEIVED: " + (secure ? "(secure) " : "(insecure) ") + commandStr);
			// inputStream: all data sent by client 
			processor.processCommand(commandStr, secure, inputStream, new ServerCommandProcessor.ProcessorListener() {
				@Override
				public boolean onProcessFinished(List<Message> messages, boolean closeConnection) {
					try {
						for (Message message : messages) {  
							if (message.getType() == MessageType.STRING) {
								outputStream.writeUTF(message.getMessage());
								outputStream.flush();
								logger.debug("SENT: " + (secure ? "(secure) " : "(insecure) ") + message.getMessage());
							} else if (message.getType() == MessageType.BYTES) {
								outputStream.write(message.getBytes());
								outputStream.flush();
								logger.debug("SENT: " + (secure ? "(secure) " : "(insecure) ") + message.getBytes().length + "B");
							} else if(message.getType() == MessageType.FILE) {
								BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(message.getFile()));
								int bufferSize = 1024;
								byte[] bufferArray = new byte[bufferSize];
								int read = 0;
								while ((read = bufferedInputStream.read(bufferArray)) != -1){
									outputStream.write(bufferArray, 0, read);
								}
								outputStream.flush();
								bufferedInputStream.close();
								logger.debug("FILE SENT: " + (secure ? "(secure) " : "(insecure) ") + message.getFile().getName());
							}
						}
						return true;
					} catch(IOException e) {
						logger.debug("Lost connection to: " + (secure ? "(secure) " : "(insecure) ") + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());
						try{
							clientSocket.close();
						} catch(IOException e1) {
							e1.printStackTrace();
							return false;
						}
						return false;
					} finally {
						if(closeConnection && !clientSocket.isClosed()) {
							try {
								clientSocket.close();
							} catch (IOException e) { 
								e.printStackTrace();
							}
							logger.debug("Close connection: " + (secure ? "(secure) " : "(insecure) ") + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());
							return false;
						}
					}
				}			
			}); 			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	interface ConnectionMessageListener {
		void onMessageReceived(Message message);
	}
}
