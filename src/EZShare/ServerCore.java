/**
 * This class is responsible for the core functionality of the server.
 * It will create a resource list and a server list and maintain it for the server.
 * And it is in charge of creating listening and exchanging threads.
 * Server will exchange the server list with a random server every X minutes (default 10min).
 * @author Sheng Wu
 * @version 1.0 29/04/2017
 */

package EZShare;
 
import java.util.ArrayList;
import java.util.Collections; 
import java.util.List; 
import java.util.Random;

import org.apache.log4j.Logger;
import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;
import org.apache.wink.json4j.OrderedJSONObject;

public class ServerCore {
	private int status;
	private ServerBean myServer;
	private ServerBean mySServer;
	private ServerConnection serverConnection; 
	private List<Resource> resources;
	private List<ServerBean> serverList;
	private List<ServerBean> serverSList;
	private static ServerCore serverCore;  
	
	Logger logger = Logger.getLogger(ServerCore.class); 
 	
	private ServerCore() {
		resources= Collections.synchronizedList(new ArrayList<>());
		serverList = Collections.synchronizedList(new ArrayList<>());
		serverSList = Collections.synchronizedList(new ArrayList<>());
	}
	
	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public ServerBean getMyServer() {
		return myServer;
	}

	public void setMyServer(ServerBean myServer) {
		this.myServer = myServer;
	}
	
	public ServerBean getMySServer() {
		return mySServer;
	}
	
	public void setMySServer(ServerBean mySServer) {
		this.mySServer = mySServer;
	}
	
	public ServerConnection getServerConnection() {
		return serverConnection;
	}

	public void setServerConnection(ServerConnection serverConnection) {
		this.serverConnection = serverConnection;
	}
	
	public List<Resource> getResources() {
		return resources;
	}

	public void setResources(List<Resource> resources) {
		this.resources = resources;
	}

	public List<ServerBean> getServerList() {
		return serverList;
	}
	
	public void setServerList(List<ServerBean> serverList) {
		this.serverList = serverList;
	}
	
	public List<ServerBean> getServerSList() {
		return serverSList;
	}
	
	public void setServerSList(List<ServerBean> serverSList) {
		this.serverSList = serverSList;
	}
	
	public static ServerCore getInstance() {
		if (serverCore == null) {
			synchronized (ServerCore.class) {
				if (serverCore == null) {
					serverCore = new ServerCore();	
				}
			}
		}
		return serverCore;
	}

	/**
	 * The method initiates the server and print out the server information in terminal.
	 * It adds its information to the server list for exchanging and creates a thread pool.
	 */
	public void initServer() {
		this.myServer = new ServerBean(ServerInfo.hostName, ServerInfo.port); 
		serverList.add(myServer); 
		this.mySServer = new ServerBean(ServerInfo.hostName, ServerInfo.sport); 
		serverSList.add(mySServer);
		
		logger.info("Starting the EZShare Server");
		logger.info("using secret: " + ServerInfo.secret);
		logger.info("using advertised hostname: " + ServerInfo.hostName);
		logger.info("insecure port: " + ServerInfo.port);
		logger.info("secure port: " + ServerInfo.sport);
		logger.info("started ");
		
		serverConnection = new ServerConnection(); // create a thread pool
	} 
	
	/**
	 * The method opens the server socket and creates two threads, one for listening incoming 
	 * client sockets and one for exchanging the server list with a random server.
	 */
	public void startServer() {
		Thread listenThread = new Thread(new Runnable() { 
			public void run() {
				logger.debug("Server insecure socket is open");
				serverConnection.handleConnection(myServer);    // open a socket and start to connect
			}
		});
		
		Thread listenSThread = new Thread(new Runnable() { 
			public void run() {
				logger.debug("Server secure socket is open");
				serverConnection.handleSecureConnection(mySServer);    // open a socket and start to connect
			}
		});
		
		Thread exchangeThread = new Thread(new Runnable()  { 
			public void run() {
				exchangeServers(myServer);
			}		
		});
		
		Thread exchangeSThread = new Thread(new Runnable()  { 
			public void run() {
				exchangeSServers(mySServer);
			}		
		});
		
		listenThread.start();  // calls the run method
		listenSThread.start();
		exchangeThread.start(); 
		// avoid server interaction violates connection interval
		try {
			Thread.sleep(ServerInfo.connectionInterval * 2000);
		} catch (InterruptedException e) { 
			e.printStackTrace();
		}
		exchangeSThread.start();
	}
	
	/**
	 * The method issues an exchange command with a random server and provides it with a copy
	 * of its entire server records. If the selected server is not reachable or a communication 
	 * error occurs, then the selected server is removed from the server list and no further 
	 * action is taken in this round.
	 */
	private void exchangeServers(ServerBean myServer) {
		logger.debug("start to exchange insecure servers: ");
		logger.debug("my insecure server:" + serverList);
		while(true) {
			try {
				Thread.sleep(ServerInfo.exchangeInterval * 1000);   //milliseconds
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			if (serverList.size() == 0) continue;
			List<ServerBean> diedServer = new ArrayList<>(); 
			JSONArray serverArray = new JSONArray(); 
			
			synchronized(serverList) {
				serverList.forEach(server -> {
					JSONObject serverObject = new JSONObject();
					try {
						serverObject.put("hostname", server.getHostname());
						serverObject.put("port", server.getPort());
					} catch (JSONException e) { 
						e.printStackTrace();
					}
					serverArray.add(serverObject);
				});
			}
	
			Random random = new Random();
			
			int r = random.nextInt(serverList.size()); 
			OrderedJSONObject messageObject = new OrderedJSONObject();
			try { 
				messageObject.put("command", "EXCHANGE");
				messageObject.put("serverList", serverArray);
			} catch (JSONException e) { 
				e.printStackTrace();
			}
			Message message = new Message(MessageType.STRING, messageObject.toString(), null, null); // a copy of server list
			List<Message> messages = serverConnection.establishConnection(serverList.get(r), message, false);  // issue an exchange cmd
			if (messages.size() == 0) {
				diedServer.add(serverList.get(r));
			} else {
				OrderedJSONObject resultObject;
				try {
					resultObject = new OrderedJSONObject(messages.get(0).getMessage());
					if (!resultObject.containsKey("response") ||
							(resultObject.containsKey("response") && !resultObject.get("response").equals("success")))
						diedServer.add(serverList.get(r));
				} catch (JSONException e) {
					e.printStackTrace();
				}				
			} 
			 
			synchronized (serverList) {
				serverList.removeAll(diedServer);
			} 
			
			logger.debug("current insecure servers:" + serverList); 
		}
	}
	
	private void exchangeSServers(ServerBean mySServer) {
		logger.debug("start to exchange secure servers: ");
		logger.debug("my secure server:" + serverSList);
		while(true) {
			try {
				Thread.sleep(ServerInfo.exchangeInterval * 1000);   //milliseconds
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (serverSList.size() == 0) continue; 
			List<ServerBean> diedSServer = new ArrayList<>(); 
			JSONArray serverSArray = new JSONArray();

			synchronized(serverSList) {
				serverSList.forEach(server -> {
					JSONObject serverObject = new JSONObject();
					try {
						serverObject.put("hostname", server.getHostname());
						serverObject.put("port", server.getPort());
					} catch (JSONException e) { 
						e.printStackTrace();
					}
					serverSArray.add(serverObject);
				});
			}

			Random random = new Random();
			 
			int rs = random.nextInt(serverSList.size()); 
			OrderedJSONObject messageSObject = new OrderedJSONObject();
			try { 
				messageSObject.put("command", "EXCHANGE");
				messageSObject.put("serverList", serverSArray);
			} catch (JSONException e) { 
				e.printStackTrace();
			}
			Message smessage = new Message(MessageType.STRING, messageSObject.toString(), null, null); // a copy of server list
			List<Message> smessages = serverConnection.establishConnection(serverSList.get(rs), smessage, true);  // issue an exchange cmd
			
			if (smessages.size() == 0) {
				diedSServer.add(serverSList.get(rs));
			} else {
				OrderedJSONObject resultObject;
				try {
					resultObject = new OrderedJSONObject(smessages.get(0).getMessage());
					if (!resultObject.containsKey("response") ||
							(resultObject.containsKey("response") && !resultObject.get("response").equals("success")))
						diedSServer.add(serverSList.get(rs));
				} catch (JSONException e) {
					e.printStackTrace();
				}				
			}  
			synchronized (serverSList) {
				serverSList.removeAll(diedSServer);
			} 			
			logger.debug("current secure servers:" + serverSList);
		}
	}
}












