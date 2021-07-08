// Serialize and deserialize packets' content

import java.io.*;
import java.util.*;

public class Utils{

	// Serialize a packet
	public static byte[] serialize(Object o) {
	    byte[] bytes = null;
	    try {
		    ByteArrayOutputStream b = new ByteArrayOutputStream();
		    ObjectOutputStream oos = new ObjectOutputStream(b);
		    oos.writeObject(o);
		    bytes = b.toByteArray();
	    } catch (Exception e) {
	    	System.err.println("Exception, serialize");
	    }
	    return bytes;
  	}

  	// Deserialize a packet
	public static Object deserialize(byte[] bytes) {
	    Object o = null;
	    ByteArrayInputStream b = new ByteArrayInputStream(bytes);
	    try {
		    ObjectInputStream ois = new ObjectInputStream(b);
		    o = ois.readObject();
	    } catch (Exception e) {
	      	System.err.println("Exception, deserialize");
	    }
	    return o;
	}
    
}
