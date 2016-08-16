package org.gudy.azureus2.core3.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;


public class T3Module {
	
	
	private HashMap<Integer, ArrayList<Integer>> pieceblockmap = new HashMap<Integer, ArrayList<Integer>>();
	private HashMap<Integer, ArrayList<byte[]>> piecedatamap = new HashMap<Integer, ArrayList<byte[]>>();
	
	Map map= Collections.synchronizedMap(pieceblockmap);
	Map map1= Collections.synchronizedMap(piecedatamap);
	
	public void putToPieceblock (int pieceno, int blockno){
		synchronized (map){
			ArrayList<Integer> temparr= new ArrayList<Integer>();
			if (pieceblockmap.containsKey(pieceno)){
				pieceblockmap.get(pieceno).add(blockno);
			}
			else {
				temparr.add(blockno);
				pieceblockmap.put(pieceno, temparr);
			}
		}
	}
		
	
	public void putToPiecedata (int pieceno, byte[] data){
		synchronized (map1){
			ArrayList<byte[]> temparr= new ArrayList<byte[]>();
			if (piecedatamap.containsKey(pieceno)){
				piecedatamap.get(pieceno).add(data);
			}
			else {
				temparr.add(data);
				piecedatamap.put(pieceno, temparr);
			}
		}
	}
	
	public ArrayList<Integer> getFromPieceblock (int pieceno){
		synchronized (map){
			ArrayList<Integer> blockarr = pieceblockmap.get(pieceno);
			return blockarr;
		}
	}
	
	public ArrayList<byte[]> getFromPiecedata (int pieceno){
		synchronized (map1){
			
			ArrayList<byte[]> blockarr = piecedatamap.get(pieceno);
			return blockarr;
		}
	}
	
	public void removeFromPieceblock (int pieceno){
		synchronized (map){
			if (pieceblockmap.get(pieceno).size()>1){
				pieceblockmap.get(pieceno).remove(0);			
			}
			else {
				pieceblockmap.remove(pieceno);
			}
		}
	}
	
	public void removeFromPiecedata (int pieceno){
		synchronized (map1){
			if (piecedatamap.get(pieceno).size()>1){
				piecedatamap.get(pieceno).remove(0);			
			}
			else {
				piecedatamap.remove(pieceno);
			}
		}
	}
}
