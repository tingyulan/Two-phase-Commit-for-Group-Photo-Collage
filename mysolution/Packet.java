// This is the package format sent between server/coordinator and users

import java.io.*;
import java.util.*;
import java.io.File; 
import java.io.FileOutputStream; 
import java.io.OutputStream; 

public class Packet implements java.io.Serializable {

	// Which phase is this packet in
	enum Type implements Serializable {
		PREPARE, VOTE, DISTRIBUTED_DECISION, ACK, NONE
  	}

	private Type type = Type.NONE;
	private String filename = null;
	private byte[] img = null;
	private String[] sources = null;
	private int id = -1;

	private boolean userDecision = false;
	private boolean serverDecision = false;

	// Packet constructor
	public Packet(){
		super();
	}

	// Packet constructor
	public Packet(Packet.Type type, int id, String filename, byte[] img) {
		this.type = type;
		this.id = id;
		this.filename = filename;
		this.img = img;
	}

	// Packet constructor
	public Packet(Packet.Type type, int id, boolean serverDecision){
		this.type = type;
		this.id = id;
		this.serverDecision = serverDecision;
	}

	// Set info for packet
	public void setSources(String[] sources) { this.sources = sources; }
	public void setId(int id) { this.id = id; }
	public void setType(Type type) { this.type = type; }
	public void setUserDecision(boolean userDecision) { this.userDecision = userDecision; }
	public void setServerDecision(boolean serverDecision) { this.serverDecision = serverDecision; }

	// Read info from packet
	public String[] getSources() { return this.sources; }
	public byte[] getImg() { return this.img; }
 	public Type getType() { return this.type; }
	public String getFilename() { return filename; }
	public int getId() { return id; }
	public boolean getUserDecision() { return userDecision; }
	public boolean getServerDecision() { return serverDecision; }

}