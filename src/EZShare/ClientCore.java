package EZShare;

import java.io.*; 
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.cli.CommandLine; 
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;
import org.apache.wink.json4j.OrderedJSONObject; 

public class ClientCore {
	private static Logger logger = Logger.getLogger(ClientCore.class);
	private ServerBean targetServer;
	private boolean secure = false;
	
	/**
	 * The method processes the command and pass it to the corresponding
	 * methods.
	 * 
	 * @param cmd
	 * @throws invalid port number
	 */
	public void processCommand(CommandLine cmd) { 
		if (cmd.hasOption("debug")) {
			logger.info("setting debug on");
			Level level = Level.toLevel(Level.DEBUG_INT);
			LogManager.getRootLogger().setLevel(level);
		} 
		
		if (!cmd.hasOption("host") || !cmd.hasOption("port")) {
			logger.error("require host and port");
			return;
		}
		
		targetServer = null;
		
		try { 
			int port = Integer.valueOf(cmd.getOptionValue("port"));
			if (port < 0 || port > 65535) {
				logger.error("port number not in range (0, 65535)");
				return;
			} 
			targetServer = new ServerBean(cmd.getOptionValue("host"), port);
		} catch (Exception e) {
			logger.error("port number not in range (0, 65535)");
			return;
		}

		if(cmd.hasOption("secure")){
			secure = true; 
		}	
		if (cmd.hasOption("publish")) {
			publish(cmd);
		} else if (cmd.hasOption("remove")) {
			remove(cmd);
		} else if (cmd.hasOption("share")) {
			share(cmd);
		} else if (cmd.hasOption("query")) {
			query(cmd);
		} else if (cmd.hasOption("fetch")) {
			fetch(cmd);
		} else if (cmd.hasOption("exchange")) {
			exchange(cmd);
		} else if (cmd.hasOption("subscribe")) {
			subscribe(cmd);
		} else {
			logger.error("missing or incorrect type for command");
			return;
		}
	}

	/**
	 * The method parses command line arguments to a Resource object.
	 * 
	 * @param cmd
	 * @param requireURI
	 *            if true, the cmd has to have a uri field
	 * @return a resource object
	 * @throws URI
	 *             not known exception
	 */
	private Resource parseResourceCmd(CommandLine cmd, boolean requireURI) {
		Resource resource = new Resource();
		// if not require uri
		// set it to "" if doesn't have a uri field
		// set it to the value if has a uri field
		if (!requireURI) {
			URI uri = null;
			if (cmd.hasOption("uri")) {
				try {
					uri = new URI(cmd.getOptionValue("uri").trim());
					resource.setUri(uri);
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
			} else {
				resource.setUri(uri);
			}
		} else if (requireURI && (!cmd.hasOption("uri") || cmd.getOptionValue("uri").equals(""))) { 
			logger.error("require uri");
			return null;
		} else {
			// set the Resource.URI = the value user enters
			URI uri = null;
			try {
				uri = new URI(cmd.getOptionValue("uri").trim());
				resource.setUri(uri);
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
		}
		if (cmd.hasOption("owner")) {
			// owner cannot be "*"
			if (cmd.getOptionValue("owner").trim().equals("*")) {
				logger.error("owner cannot be \"*\"");
				return null;
			}
			resource.setOwner(cmd.getOptionValue("owner").trim());
		} else {
			resource.setOwner("");
		}
		if (cmd.hasOption("name")) {
			resource.setName(cmd.getOptionValue("name").trim());
		} else {
			resource.setName("");
		}
		if (cmd.hasOption("channel")) {
			resource.setChannel(cmd.getOptionValue("channel").trim());
		} else {
			resource.setChannel("");
		}
		if (cmd.hasOption("description")) {
			resource.setDescription(cmd.getOptionValue("description").trim());
		} else {
			resource.setDescription("");
		}
		List<String> tagList = new ArrayList<>();
		if (cmd.hasOption("tags")) {
			String[] tags = cmd.getOptionValue("tags").split(",");
			for (int i = 0; i < tags.length; i++) {
				tagList.add(tags[i].trim());
			}
		}
		resource.setTags(tagList);
		return resource;
	}
	
	/**
	 * The method is to issue a publish command. The publish command is to
	 * publish a resource to the server. Receive response (error or success)
	 * from the server and print the message out.
	 * 
	 * @param cmd
	 */
	private void publish(CommandLine cmd) {
		Resource resource = parseResourceCmd(cmd, true);
		if (resource == null)
			return;
		OrderedJSONObject jsonObject = new OrderedJSONObject();
		try {
			jsonObject.put("command", "PUBLISH");
			jsonObject.put("resource", Resource.toJson(resource));
		} catch (org.apache.wink.json4j.JSONException e) {
			e.printStackTrace();
		}
		logger.info("publishing to " + cmd.getOptionValue("host") + ":" + cmd.getOptionValue("port"));
		ClientConnection.establishConnection(targetServer, new Message(jsonObject.toString()), secure);	
	} 

	/**
	 * The method is to issue a query command. The query command is to match the
	 * template against existing resources using some rules. Receive response
	 * (error or success) from the server and print the message out.
	 * 
	 * @param cmd
	 */
	private void query(CommandLine cmd) {
		Resource resource = parseResourceCmd(cmd, false);
		if (resource == null)
			return;
		OrderedJSONObject jsonObject = new OrderedJSONObject();
		try {
			jsonObject.put("command", "QUERY");
			jsonObject.put("relay", true);
			jsonObject.put("resourceTemplate", Resource.toJson(resource));
		} catch (org.apache.wink.json4j.JSONException e) {
			e.printStackTrace();
		} 
		logger.info("quering ");
		ClientConnection.establishConnection(targetServer, new Message(jsonObject.toString()),secure);
	}

	/**
	 * The method is to issue an exchange command. The exchange command will
	 * tell a server about a list of other servers.And the server can process
	 * any valid server and ignore others. Receive response (error or success)
	 * from the server and print the message out.
	 * 
	 * @param cmd
	 */
	private void exchange(CommandLine cmd) {
		if (!cmd.hasOption("servers")){
			logger.error("require servers");
			return;
		}
		String[] serverStrings = cmd.getOptionValue("servers").split(",");
		OrderedJSONObject jsonObject = new OrderedJSONObject();
		try {
			jsonObject.put("command", "EXCHANGE");
		} catch (JSONException e1) { 
			e1.printStackTrace();
		}
		JSONArray serverArray = new JSONArray();
		for(int i=0; i<serverStrings.length; i++) {
			JSONObject serverObject = new JSONObject();
			String hostname = serverStrings[i].split(":")[0].trim();
			int port = Integer.valueOf(serverStrings[i].split(":")[1].trim());
		
			try {
				serverObject.put("hostname", hostname);
				serverObject.put("port", port);
				serverArray.add(serverObject);
			} catch (JSONException e) { 
				e.printStackTrace();
			}	
		}
		try {
			jsonObject.put("serverList", serverArray);
		} catch (JSONException e) { 
			e.printStackTrace();
		}
		ClientConnection.establishConnection(targetServer, new Message(jsonObject.toString()),secure);
	}

	private void subscribe(CommandLine cmd){
		Resource resource = parseResourceCmd(cmd, false);
		if(resource == null){
			return;
		}
		OrderedJSONObject subscribeJsonObject = new OrderedJSONObject();
		OrderedJSONObject unsubscribeJsonObject = new OrderedJSONObject();
		Random random = new Random(System.currentTimeMillis());
		int id = random.nextInt();
		try {
			subscribeJsonObject.put("command", "SUBSCRIBE");
			subscribeJsonObject.put("relay", true);
			subscribeJsonObject.put("id", id + "");
			subscribeJsonObject.put("resourceTemplate", Resource.toJson(resource));
			unsubscribeJsonObject.put("command", "UNSUBSCRIBE");
			unsubscribeJsonObject.put("id", id + "");
		} catch (JSONException e1) { 
			e1.printStackTrace();
		}
		logger.info("subscribing ");
		logger.info("SENT: " + (secure ? "(secure) " : "(insecure) ") + subscribeJsonObject.toString());
		
		ClientConnection.establishPersistentConnection(targetServer, new Message(subscribeJsonObject.toString()), new ClientConnection.KeyboardListener() {
			@Override
			public boolean onKeyPressed(DataOutputStream outputStream, String string) {
				try {
					outputStream.writeUTF(unsubscribeJsonObject.toString());
					outputStream.flush();
					logger.info("SENT: " + (secure ? "(secure) " : "(insecure) ") + unsubscribeJsonObject.toString());
				} catch (IOException e) {
					//e.printStackTrace();
				}
				return true;
			}
		}, new ClientConnection.MessageListener() {
			@Override
			public boolean onMessageReceived(Message message) {
				logger.info("RECEIVED: " + (secure ? "(secure) " : "(insecure) ") +  message.getMessage()); 
				return false;
			}
		}, secure); 
	}
	
	/**
	 * This method is to set chunk size for receiving files.
	 * 
	 * @param fileSizeRemaining
	 * @return chunksize
	 */
	private static int setChunkSize(long fileSizeRemaining) {
		int chunkSize = 1024 * 1024;
		if (fileSizeRemaining < chunkSize) {
			chunkSize = (int) fileSizeRemaining;
		}
		return chunkSize;
	}
	
	/**
	 * The method is to issue a remove command. The remove command will remove
	 * the resource on the server. Receive response (error or success) from the
	 * server and print the message out.
	 * 
	 * @param cmd
	 */
	private void remove(CommandLine cmd) {
		Resource resource = parseResourceCmd(cmd, true);
		if (resource == null)
			return;
		OrderedJSONObject jsonObject = new OrderedJSONObject();
		try {
			jsonObject.put("command", "REMOVE");
			jsonObject.put("resource", Resource.toJson(resource));
		} catch (org.apache.wink.json4j.JSONException e) {
			e.printStackTrace();
		}  
		ClientConnection.establishConnection(targetServer, new Message(jsonObject.toString()),secure);
	}

	/**
	 * The method is to issue a share command. The share command will share a
	 * file resource to the server. Receive response (error or success) from the
	 * server and print the message out.
	 * 
	 * @param cmd
	 */
	private void share(CommandLine cmd) {
		if (!cmd.hasOption("secret")) {
			logger.error("require secret");
			return;
		}
		Resource resource = parseResourceCmd(cmd, true);
		if (resource == null)
			return;
		OrderedJSONObject jsonObject = new OrderedJSONObject();
		try {
			jsonObject.put("command", "SHARE");
			jsonObject.put("secret", cmd.getOptionValue("secret"));
			jsonObject.put("resource", Resource.toJson(resource));
		} catch (org.apache.wink.json4j.JSONException e) {
			e.printStackTrace();
		}
		logger.info("sharing to " + cmd.getOptionValue("host") + ":" + cmd.getOptionValue("port"));
		ClientConnection.establishConnection(targetServer, new Message(jsonObject.toString()),secure);
	}

	/**
	 * The method is to issue a fetch command. The fetch command will download a
	 * file from the server. Receive response (error or success) from the server
	 * and print the message out.
	 * 
	 * @param cmd
	 */
	private void fetch(CommandLine cmd) {
		Resource resource = parseResourceCmd(cmd, true);
		if (resource == null)
			return;
		OrderedJSONObject jsonObject = new OrderedJSONObject();
		try {
			jsonObject.put("command", "FETCH");
			jsonObject.put("resourceTemplate", Resource.toJson(resource));
		} catch (org.apache.wink.json4j.JSONException e1) {
			e1.printStackTrace();
		} 
		logger.info("downloading "); 
		Socket socket = null;
		try {
			if (!secure) {
				socket = new Socket(targetServer.getHostname(), targetServer.getPort());
			} else {
				socket= (SSLSocket) SSLSocketFactory.getDefault().createSocket(targetServer.getHostname(),targetServer.getPort());
			}
			DataInputStream inputStream = new DataInputStream(socket.getInputStream());
			DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
			outputStream.writeUTF(jsonObject.toString());
			outputStream.flush();
			String response = null;
			if ((response = inputStream.readUTF()) != null) {
				logger.info("RECEIVED: " + response);
				if (response.contains("error"))
					return;
				Long size = (long) 0;
				String resourceInfoStr = inputStream.readUTF(); 
				logger.info("RECEIVED: " + resourceInfoStr);
				JSONObject resourceInfo;
				try {
					resourceInfo = new JSONObject(resourceInfoStr);
					size = resourceInfo.getLong("resourceSize");
				} catch (JSONException e) {
					logger.error("no resource existed");
					return;
				}
				String fileName = resource.getUri().getPath().split("/")[resource.getUri().getPath().split("/").length - 1];
				RandomAccessFile file = new RandomAccessFile(fileName, "rw");
				int chunkSize = setChunkSize(size);
				byte[] buffer = new byte[chunkSize];
				int number;
				while ((number = inputStream.read(buffer)) > 0) {
					file.write(Arrays.copyOf(buffer, number));
					size -= number;
					chunkSize = setChunkSize(size);
					buffer = new byte[chunkSize];
					if (size == 0) {
						file.close();
						break;
					}
				}
				String data = null;
				if ((data = inputStream.readUTF()) != null) { 
					logger.info("RECEIVED: " + data);
				}
				inputStream.close();
				outputStream.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}

