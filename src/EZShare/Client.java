/**
 * This is the main class of client. A client is used to instruct the server to share the files.
 * For example, clients can request a shared file to be downloaded to them. Communications are via 
 * TCP. All messages are in JSON format, except file contents, one JSON message per line. File 
 * contents are transmitted as byte sequences, mixed between JSON messages. Interactions are 
 * synchronous request-reply, with a single request per connection.
 * @author Sheng Wu
 * @version 1.0 29/04/2017
 *
 */

package EZShare;

import java.io.InputStream;
import java.security.KeyStore;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory; 

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.Logger;

public class Client { 
	private static Logger logger = Logger.getLogger(Client.class); 
	
	/**
	 * The main method will create a command line parser to process the
	 * arguments the user enter in terminal.
	 * 
	 * @param args the user enter in terminal
	 * @throws invalid command
	 */
	public static void main(String[] args) {
		Options options = new Options();
		options.addOption("channel", true, "channel");
		options.addOption("debug", false, "print debug information");
		options.addOption("description", true, "resource description");
		options.addOption("exchange", false, "exchange server list with server");
		options.addOption("fetch", false, "fetch resources from server");
		options.addOption("host", true, "server host, a domain name or IP address");
		options.addOption("name", true, "resource name");
		options.addOption("owner", true, "owner");
		options.addOption("port", true, "server port, an integer");
		options.addOption("publish", false, "publish resource on server");
		options.addOption("query", false, "query for resources from server");
		options.addOption("remove", false, "remove resource from server");
		options.addOption("secret", true, "secret");
		options.addOption("servers", true, "server list, host1:port1,host2:port2,...");
		options.addOption("share", false, "share resource on server");
		options.addOption("tags", true, "resource tags, tag1,tag2,tag3,...");
		options.addOption("uri", true, "resource URI");
		options.addOption("subscribe", false, "subscribe for resources from server");
		options.addOption("secure", false, "secure connection");

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;

		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			logger.error("invalid command");
			return;
		}
		
		InputStream keystoreInput = Thread.currentThread().getContextClassLoader().getResourceAsStream("client.jks");
		InputStream truststoreInput = Thread.currentThread().getContextClassLoader().getResourceAsStream("trust.jks");
		try {
			setSSLFactories(keystoreInput, "comp90015", truststoreInput);
		} catch (Exception e) { 
			e.printStackTrace();
		}
	    
		ClientCore core = new ClientCore();
		core.processCommand(cmd);
	}
	
	private static void setSSLFactories(InputStream keyStream, String keyStorePassword, InputStream trustStream) throws Exception {    
		KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());     
		char[] keyPassword = keyStorePassword.toCharArray(); 
		keyStore.load(keyStream, keyPassword);
		KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());    
		keyFactory.init(keyStore, keyPassword); 
		KeyManager[] keyManagers = keyFactory.getKeyManagers(); 
		KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());   
		trustStore.load(trustStream, keyPassword); 
		TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());    
		trustFactory.init(trustStore); 
		TrustManager[] trustManagers = trustFactory.getTrustManagers(); 
		SSLContext sslContext = SSLContext.getInstance("SSL");
		sslContext.init(keyManagers, trustManagers, null);
		SSLContext.setDefault(sslContext);    
	}
}

