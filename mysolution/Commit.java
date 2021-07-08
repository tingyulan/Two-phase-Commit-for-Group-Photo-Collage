import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.io.File; 
import java.io.FileOutputStream; 
import java.io.OutputStream; 


public class Commit {
	private static ProjectLib PL;
	private String filename;
	private byte[] img;
	private String[] sources;
	private int id;
	private int timeout = 6000; 

	private ConcurrentHashMap<String, ArrayList<String>> hm_user_sources = new ConcurrentHashMap<String, ArrayList<String>>();
	private ArrayList<String> allUsers = new ArrayList<String>();

	private ConcurrentHashMap<String, Boolean> hm_user_votes = new ConcurrentHashMap<String, Boolean>();
	private ArrayList<Boolean> allVotes = new ArrayList<Boolean>();

	private ArrayList<String> allAcks = new ArrayList<String>();

	private AtomicInteger pendingDecisions;
	private AtomicInteger pendingAcks;

	private BufferedWriter bw;

	
	public Commit(){
		super();
	}

	// Set basic info for a commit
	public Commit(ProjectLib PL, String filename, byte[] img, String[] sources, int commitNumber, boolean flg_recovery) {
		this.PL = PL;
		this.filename = filename;
		this.img = img;
		this.sources = sources;
		this.id = commitNumber;

		recordListOfSites();
		// Only send record list to user nodes if it is not in a recovery process
		if(flg_recovery==false){ 
			sendToUsers();
		}
	}

	// return how many user nodes involve in this transaction
	public int getUserSize(){
		return allUsers.size();
	}

	// write to the log file
	public void writeLine(String line){
		try{
			bw.write(line);
			bw.newLine();
			bw.flush();
			PL.fsync();
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	// Assign each user node its sources.
	public void recordListOfSites() {
		String sources_record = "";
		for(String src: sources){
            String[] src_split = src.split(":");
            ArrayList<String> files;
            if(hm_user_sources.containsKey(src_split[0])){
                files = hm_user_sources.get(src_split[0]);
            }else{
            	allUsers.add(src_split[0]);
                files = new ArrayList<String>();
            }
            files.add(src_split[1]);
            hm_user_sources.put(src_split[0], files);
            sources_record = sources_record + src + "*";
        }

		pendingDecisions = new AtomicInteger(allUsers.size());
		pendingAcks = new AtomicInteger(allUsers.size());

		// Record commit and collage to log files
		String logName = "commit_" + Integer.toString(id);
		File fout = new File(logName);
		try{
			if(fout.exists()){
				FileOutputStream fos = new FileOutputStream(fout, true);
				bw = new BufferedWriter(new OutputStreamWriter(fos));
			}else{
				FileOutputStream fos = new FileOutputStream("img_"+Integer.toString(id)); 
				fos.write(img);
				fos.close();
				fos.flush();
	  
				fos = new FileOutputStream(fout);
				bw = new BufferedWriter(new OutputStreamWriter(fos));
				writeLine(filename);
				writeLine(sources_record);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	// Send prepare message to user nodes
	// Also, set a timeout threshold.
	// If server does not get vote before time out, directly lable it as a false vote
	public void sendToUsers() {
		ProjectLib.Message msg = null;
		for(String node: allUsers) {
			Packet pk = new Packet(Packet.Type.PREPARE, id, filename, img); 

			ArrayList<String> fileList = hm_user_sources.get(node);
			String[] files = new String[fileList.size()];
			files = fileList.toArray(files);
			pk.setSources(files);

			msg = new ProjectLib.Message(node, Utils.serialize(pk));
			writeLine("PREPARE*"+msg.addr); // Record to the log file
			PL.sendMessage(msg);
		}
		Thread thread = new Thread( new voteTimeOut(msg) );
		thread.start();
	}

	// If server does not get vote before time out, directly lable it as a false vote
	public class voteTimeOut implements Runnable{
		private ProjectLib.Message msg;
		voteTimeOut(ProjectLib.Message msg){
			this.msg = msg;
		}

		public void run(){
			// wait for timeout
			try{ Thread.sleep(timeout); }
			catch(Exception e){ e.printStackTrace(); }

			// Check if all users have voted
			if(pendingDecisions.get()==0){return;}
			for(String node: allUsers) {
				if( !hm_user_votes.containsKey(node) ){
					writeLine( "VOTE*"+msg.addr+"*" + Integer.toString(0) );

					allVotes.add(false);
					hm_user_votes.put(node, false);
				}
			}
			sendDecisionToUsers(false);
		}
	}

	// Send coordinator's decision to all user nodes
	public void sendDecisionToUsers(boolean decision){
		Packet pk = new Packet(Packet.Type.DISTRIBUTED_DECISION, id, decision);

		// Distribute decision to users
		for(String node:allUsers) {
			ArrayList<String> fileList = hm_user_sources.get(node);
			String[] files = new String[fileList.size()];
			files = fileList.toArray(files);
			pk.setSources(files);

			ProjectLib.Message reply_msg = new ProjectLib.Message(node, Utils.serialize(pk));
			PL.sendMessage(reply_msg);
			Thread thread = new Thread( new traceAck(reply_msg) );
			thread.start();
		}
	}

	// Written the collage that is published to server's working directory
	public void writeImageToServer(String wfilename, byte[] wimg){
		try{
			FileOutputStream f = new FileOutputStream(wfilename);
			f.write(wimg);
			f.close();
		}catch (Exception e){
			System.err.println("Exception, Write file to server directory");
		}
	}

	// Make sure to get all ack from user nodes.
	// If does not get an ack, resend the coordinator final decision to user again.
	public class traceAck implements Runnable{
		private ProjectLib.Message msg;
		traceAck(ProjectLib.Message msg){
			this.msg = msg;
		}

		public void run(){
			while(true){
				try{
					Thread.sleep(timeout);
				}catch(Exception e){ e.printStackTrace(); }

				if(allAcks.contains(msg.addr)){ return; }
				PL.sendMessage(msg);
			}
		}
	}

	// Record vote get from user nodes
	public void handleVote(ProjectLib.Message msg, Packet pk){
		if( !hm_user_votes.containsKey(msg.addr) ){
			writeLine( "VOTE*"+msg.addr+"*" + Boolean.toString(pk.getUserDecision()) );

			// Record votes
			allVotes.add(pk.getUserDecision());
			hm_user_votes.put(msg.addr, pk.getUserDecision());
			int wait = pendingDecisions.decrementAndGet();

			// If get all votes, make final decision
			if(wait == 0) {
				boolean decision = false;
				if(!allVotes.contains(false)) {
					decision = true;
				}
				writeLine("DECISION*" + Boolean.toString(decision));
				// Send coordinator's decision to all users
				sendDecisionToUsers(decision);

				//Write published collage to Server's working directory
				if (decision==true){
					String filename = pk.getFilename();
					byte[] img = pk.getImg();
					try{
						FileOutputStream f = new FileOutputStream(filename);
						f.write(img);
						f.close();
					}catch (Exception e){
						System.err.println("Exception, Write file to server directory");
					}
				}
			}
		}
	}

	// Record ACK get from user nodes
	public void handleAck(ProjectLib.Message msg){
		writeLine("ACK*"+msg.addr);
		allAcks.add(msg.addr);
		pendingAcks.decrementAndGet();
	}
	

	// Handle all messages get from user nodes
	public void handleMessage(ProjectLib.Message msg) {
		Packet pk = (Packet) Utils.deserialize(msg.body);

		if( pk.getType() == Packet.Type.VOTE ){ handleVote(msg, pk); }
		else if(pk.getType() == Packet.Type.ACK){ handleAck(msg); }
	}

}