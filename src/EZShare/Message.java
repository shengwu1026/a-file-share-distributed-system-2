/**
 * The class is to write some getters and setters for the message object.
 * @author Sheng Wu
 * @version 1.0 29/04/2017
 */

package EZShare; 

import java.io.File;
import java.util.ArrayList;
import java.util.List; 

public class Message {
	private MessageType type;
	private String message;
	private byte[] bytes;
	private File file;
	
	public Message() {}
	
	public Message(String message){
		this.type = MessageType.STRING;
		this.message = message;
	}
	
	public Message(MessageType type, String message, byte[] bytes, File file){
		this.type = type;
		this.message = message;
		this.bytes = bytes;
		this.file = file;
	}
	
	// for subscribe
	public static List<Message> makeAMessage(String string) {
		List<Message> messages = new ArrayList<>();
		messages.add(new Message(string));
		return messages;
	}
	
	// for subscribe
	public static List<Message> makeMessage(Message message) {
		List<Message> messages = new ArrayList<>();
		messages.add(message);
		return messages;
	}
	
	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}
 
	public MessageType getType() {
		return type;
	}

	public void setType(MessageType type) {
		this.type = type;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public byte[] getBytes() {
		return bytes;
	}

	public void setBytes(byte[] bytes) {
		this.bytes = bytes;
	}

}
