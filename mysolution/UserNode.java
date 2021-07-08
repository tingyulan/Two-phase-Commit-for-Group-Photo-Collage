import java.io.*;
import java.util.*;
import java.io.File; 
import java.io.FileOutputStream; 
import java.io.OutputStream; 
import java.io.BufferedReader;


public class UserNode implements ProjectLib.MessageHandling{

	private static ProjectLib PL;
	private static String userId;
	private static BufferedWriter bw;

	private static ArrayList<String> serverLockImages = new ArrayList<String>();
	private static ArrayList<String> publishedImages = new ArrayList<String>();

	public UserNode( String id ) {
		userId = id;
	}

	// Handle prepare message sent from coordinator
	public void handlePrepare(Packet pk){
		String[] sources = pk.getSources();
		boolean vote = true;

		// Check source is in use or not
		ArrayList<String> userLockImages = new ArrayList<String>(); // temparary locked images for this preparation
		for(String src : sources) {
			synchronized(serverLockImages) {
				if(serverLockImages.contains(src) || publishedImages.contains(src)) {
					vote = false;
					break;
				} else {
					userLockImages.add(src);
					serverLockImages.add(src);
				}
			}
		}

		// Unlock temporary locked images if vote is false
		boolean finalVote = false;
		if (vote==false){
			for(int i=0; i<userLockImages.size(); i++){
				serverLockImages.remove( userLockImages.get(i) );
			}
		}else{ // Make final vote via askUser
			finalVote = PL.askUser(pk.getImg(), sources);
		}

		// Send vote message to Server
		pk.setUserDecision(finalVote);
		pk.setType(Packet.Type.VOTE);
		ProjectLib.Message reply_msg = new ProjectLib.Message("Server", Utils.serialize(pk));
		PL.sendMessage(reply_msg);
	}
	
	// Handle coordinator's final decision
	public void handleDistributedDecision(Packet pk){
		String[] sources = pk.getSources();

		// If final decision is true, publish that image.
		if(pk.getServerDecision()==true) {
			try {
				for(String src: sources) {
					File file = new File(src);
					if(file.exists()){
						file.delete();
						publishedImages.add(src);
						serverLockImages.remove(src);
					}
				}
			} catch(Exception e) {
				e.printStackTrace();
			}			
		}else{ // unlock images if final decision is false
			for(String src: sources) {
				synchronized(serverLockImages) {
					if(serverLockImages.contains(src)){
						serverLockImages.remove(src);
					}
				}
			}
		}

		// Send ack messages back to server
		pk.setType(Packet.Type.ACK);
		ProjectLib.Message reply_msg  = new ProjectLib.Message("Server", Utils.serialize(pk));
		PL.sendMessage(reply_msg);
	}

	// Handle all messages
	public boolean deliverMessage( ProjectLib.Message msg ) {
		Packet pk = (Packet) Utils.deserialize(msg.body);

		if(pk.getType() == Packet.Type.PREPARE){ handlePrepare(pk); }
		else if(pk.getType() == Packet.Type.DISTRIBUTED_DECISION){ handleDistributedDecision(pk); }

		return true;
	}

	// Start a user node, and check log files.
	public static void main( String args[] ) throws Exception {
		if (args.length != 2) throw new Exception("Need 2 args: <port> <id>");
		UserNode UN = new UserNode(args[1]);
		PL = new ProjectLib( Integer.parseInt(args[0]), args[1], UN );

		String filename = "userNode_"+args[1];
		File fout = new File(filename);
		boolean exists = fout.exists();

		// Recover and set up log files
		FileOutputStream fos;
		if(exists){
			recovery(filename);
			fos = new FileOutputStream(fout, true); //append file
		}else{
			fos = new FileOutputStream(fout);
		}
		bw = new BufferedWriter(new OutputStreamWriter(fos));
	}

	// Read log files to recover when a user node starts.
	public static void recovery(String filename){
		try{
			BufferedReader reader = new BufferedReader(new FileReader(filename));
			String line = reader.readLine();
			while(line != null){
				publishedImages.add(line);
				line = reader.readLine();
			}
			reader.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}

}

