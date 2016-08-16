package org.gudy.azureus2.core3.util;

import java.nio.ByteBuffer;
import java.util.ArrayList;


public class T2Module {
	
	// public
	public class RecElement {
		private int piecenumb;
		private byte[] initiator = null;
		
		public RecElement(int _piecenumb, byte[] _initiator){
			this.piecenumb = _piecenumb;
			this.initiator = _initiator;
		}
		
		public int getRecPiece(){
			return piecenumb;
		}
		
		public byte[] getRecInitiator(){
			return initiator;
		}
	}
	
	private ArrayList<RecElement> conmap = new ArrayList<RecElement>();
	
	
	public void putToContinuation(int pieceno, byte[] initiationpeer){
		conmap.add(new RecElement(pieceno, initiationpeer));
		//
		}
	
	public ArrayList<byte[]> Reciprocation(){
		if (conmap.isEmpty()){
			return null;
		}
		else {
			RecElement re = conmap.remove(0);
			ArrayList<byte[]> recmod = new ArrayList<byte[]>();
			byte[] byte1 = ByteBuffer.allocate(4).putInt(re.getRecPiece()).array();
			byte[] byte2 = re.getRecInitiator();
			
			recmod.add(byte1);
			recmod.add(byte2);
			
			return recmod;
			//recmod.add(re.getRecPiece().toBytes());
		}
	}
	}




