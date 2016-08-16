/**
 * 
 */
package org.gudy.azureus2.core3.util;

import java.util.*;


/**
 * @author Varun
 *
 */
public class T1Module {

	/**
	 * private final HashMap<BTPiece,DiskManagerReadRequest> 	queued_messages 	= new HashMap<BTPiece, DiskManagerReadRequest>();
  
	 * @param args
	 */
	
	private HashMap<Integer, ArrayList<byte[]>> translatormap = new HashMap<Integer, ArrayList<byte[]>>();
	private HashMap<Integer,byte[]> piecekeymap = new HashMap<Integer, byte[]>();
	
	Map map= Collections.synchronizedMap(translatormap);
	Map map1= Collections.synchronizedMap(piecekeymap);
	
	public void putToTranslator (int pieceno, byte[] terminationpeer){
		synchronized(map){
			if (translatormap.containsKey(pieceno)){
				translatormap.get(pieceno).add(terminationpeer);
			}
			else {
				ArrayList<byte[]> terminationlist = new ArrayList<byte[]>();
				terminationlist.add(terminationpeer);
				translatormap.put(pieceno, terminationlist);
			}
		}
	}
	
	public ArrayList<byte[]> getFromTranslator (int pieceno){
		synchronized(map){
			ArrayList<byte[]> temparr= new ArrayList<byte[]>();
			temparr = translatormap.get(pieceno);
			return temparr;
		}
	}
	
	public void putToPiecekey (int pieceno, byte[] key){
		synchronized (map1){
			piecekeymap.put(pieceno, key);
			}
		}
	public boolean checkPiecekey (int pieceno){
		synchronized(map1){
			return piecekeymap.containsKey(pieceno);
		}
	}
	public byte[] getFromPiecekey (int pieceno){
		synchronized (map1){
			byte[] pk = null;
			pk = piecekeymap.get(pieceno);
			return pk;
		}
	}
	
	public void removeFromTranslator (int pieceno, byte[] terminationpeer){
		synchronized (map){
			if (translatormap.get(pieceno).size()>1){
				translatormap.get(pieceno).remove(terminationpeer);			
			}
			else {
				translatormap.remove(pieceno);
			}
		}
	}
	
	public void removeFromPiecekey (int pieceno){
		
		synchronized(map){
			piecekeymap.remove(pieceno);
		}
	}
}
