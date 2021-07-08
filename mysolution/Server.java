import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.nio.file.Files;
import java.nio.file.*;

public class Server implements ProjectLib.CommitServing {

	private static ProjectLib PL;
	private static AtomicInteger idGenerator = new AtomicInteger(0);
	private static ConcurrentHashMap<Integer, Commit> hm_IdCommit = new ConcurrentHashMap<Integer,Commit>();

	// Start a new commit
	public void startCommit( String filename, byte[] img, String[] sources ) {
		int id = idGenerator.getAndIncrement(); // Each commit has a unique id number
		Commit commit = new Commit(PL, filename, img, sources, id, false);
		hm_IdCommit.put(id, commit); // record commit and its id in hashmap
	}

	public static void main ( String args[] ) throws Exception {
		if (args.length != 1) throw new Exception("Need 1 arg: <port>");
		Server srv = new Server();
		PL = new ProjectLib( Integer.parseInt(args[0]), srv );
		recovery();

		// main loop
		while (true) {
			ProjectLib.Message msg = PL.getMessage();
			Packet pk =(Packet) Utils.deserialize(msg.body);
			
			int id = pk.getId();
			Commit commit = hm_IdCommit.get(id);
			commit.handleMessage(msg);
		}

	}

	// When a server starts, check if there are exsiting log files.
	// Go through all log files and continue or abort transactions.
	private static void recovery(){
		for(int id=0; id<Integer.MAX_VALUE; id++){
			String filename = null;
			byte[] img = null;
			String[] sources;
			Commit commit=null;

			BufferedReader reader=null;
			String line;
			try{
				// check if a log file exist or not.
				// Since commit id numbers are in order, if we can not find a log file,
				// it means we have already read all log files.
				String string_path = "commit_" + Integer.toString(id);
				File f = new File(string_path); 
				if(!f.exists()){ break; }
				FileInputStream fis = new FileInputStream(string_path);
				reader = new BufferedReader(new InputStreamReader(fis));

				// read basic commit info
				filename = reader.readLine();
				line = reader.readLine();
				sources = line.split("\\*");

				Path path = Paths.get("img_" + Integer.toString(id));
				img = Files.readAllBytes(path);

				// reconstruct a commit
				commit = new Commit(PL, filename, img, sources, id, true); 
				hm_IdCommit.put(id, commit);
			}catch (Exception e){
				e.printStackTrace();
			}

			// Read log info
			int numUser = commit.getUserSize();
			int num_prepare = 0;
			int num_ack = 0;
			int num_vote= 0;
			boolean decision = true;
			String final_decision = null;
			try{
				line = reader.readLine();
				while( line != null ){
					String[] split_line = line.split("\\*");
					if(split_line[0].equals("PREPARE")){ num_prepare++; }
					else if(split_line[0].equals("ACK")){ num_ack++; }
					else if(split_line[0].equals("VOTE")){
						num_vote++;
						if(split_line[2].equals("false")){
							decision = false;
						}
					}else if(split_line[0].equals("DECISION")){
						final_decision = split_line[1];
					}
					line = reader.readLine();
				}
				reader.close();
			}catch (Exception e){
				e.printStackTrace();
			}


			// Abort or continue transaction base on log
			if(num_ack == numUser){ // This transaction has been committed
				// do nothing
			}else if(final_decision!=null){ // Server has made final decision
				if(final_decision.equals("false")){
					commit.sendDecisionToUsers(false);
				}else{
					commit.sendDecisionToUsers(true);
					commit.writeImageToServer(filename, img);
				}
			}else if(num_vote == numUser){ // All user nodes has voted
				commit.sendDecisionToUsers(decision);
				if(decision==true){
					commit.writeImageToServer(filename, img);
				}
			}else{ // abort transaction
				commit.sendDecisionToUsers(false);
			}
						
		}
	}

}

