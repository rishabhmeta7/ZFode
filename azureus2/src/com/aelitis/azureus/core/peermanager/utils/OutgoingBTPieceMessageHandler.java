/*
 * Created on Jul 19, 2004
 * Created by Alon Rohter
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.aelitis.azureus.core.peermanager.utils;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.gudy.azureus2.core3.disk.*;
import org.gudy.azureus2.core3.peer.PEPeer;
import org.gudy.azureus2.core3.peer.impl.PEPeerTransport;
import org.gudy.azureus2.core3.util.*;

import com.aelitis.azureus.core.networkmanager.OutgoingMessageQueue;
import com.aelitis.azureus.core.peermanager.messaging.*;
import com.aelitis.azureus.core.peermanager.messaging.bittorrent.*;


/**
 * Front-end manager for handling requested outgoing bittorrent Piece messages.
 * Peers often make piece requests in batch, with multiple requests always
 * outstanding, all of which won't necessarily be honored (i.e. we choke them),
 * so we don't want to waste time reading in the piece data from disk ahead
 * of time for all the requests. Thus, we only want to perform read-aheads for a
 * small subset of the requested data at any given time, which is what this handler
 * does, before passing the messages onto the outgoing message queue for transmission.
 */
public class OutgoingBTPieceMessageHandler {
  private final PEPeer					peer;
  private final OutgoingMessageQueue 	outgoing_message_queue;
  private 		byte					piece_version;
  
  private final LinkedList<DiskManagerReadRequest>			requests 			= new LinkedList<DiskManagerReadRequest>();
  private final ArrayList<DiskManagerReadRequest>			loading_messages 	= new ArrayList<DiskManagerReadRequest>();
  private final HashMap<BTPiece,DiskManagerReadRequest> 	queued_messages 	= new HashMap<BTPiece, DiskManagerReadRequest>();
  
  private final AEMonitor	lock_mon	= new AEMonitor( "OutgoingBTPieceMessageHandler:lock");
  private boolean destroyed = false;
  private int request_read_ahead = 2;

  private OutgoingBTPieceMessageHandlerAdapter	adapter;

  /**
   * Create a new handler for outbound piece messages,
   * reading piece data from the given disk manager
   * and transmitting the messages out the given message queue.
   * @param disk_manager
   * @param outgoing_message_q
   */
  public 
  OutgoingBTPieceMessageHandler( 
	PEPeer									_peer,
	OutgoingMessageQueue 					_outgoing_message_q,
	OutgoingBTPieceMessageHandlerAdapter	_adapter,
	byte									_piece_version ) 
  {
	peer					= _peer;
    outgoing_message_queue 	= _outgoing_message_q;
    adapter					= _adapter;
    piece_version			= _piece_version;
    
    outgoing_message_queue.registerQueueListener( sent_message_listener );
  }
  
  public void
  setPieceVersion(
	byte	version )
  {
	  piece_version = version;
  }
  
  
  
  private final DiskManagerReadRequestListener read_req_listener = new DiskManagerReadRequestListener() {
    public void readCompleted( DiskManagerReadRequest request, DirectByteBuffer data ) {
      try{
      	lock_mon.enter();

      	if( !loading_messages.contains( request ) || destroyed ) { //was canceled
      	  data.returnToPool();
      	  return;
      	}
      	loading_messages.remove( request );
      	/*
      	 * BTPiece definition changed here 
      	 */
        
        // byte[] keygenerated = peer.getId();
      	 final byte[] receipt = new byte[] { (byte)0xaa, (byte)0xaa, (byte)0xaa,
             	(byte)0xaa, (byte)0xaa, (byte)0xaa, (byte)0xaa, (byte)0xaa,
             	(byte)0xaa, (byte)0xaa, (byte)0xaa, (byte)0xaa, (byte)0xaa,
             	(byte)0xaa, (byte)0xaa, (byte)0xaa, (byte)0xaa, (byte)0xaa,
             	(byte)0xaa, (byte)0xaa };
         final byte[] nothing = new byte[] { (byte)0x88, (byte)0x88, (byte)0x88, 
             	(byte)0x88, (byte)0x88, (byte)0x88, (byte)0x88, (byte)0x88, 
             	(byte)0x88, (byte)0x88, (byte)0x88, (byte)0x88, (byte)0x88,
             	(byte)0x88, (byte)0x88, (byte)0x88, (byte)0x88, (byte)0x88, 
             	(byte)0x88, (byte)0x88}; 
      	
      		String algorithm = new String("AES");
        
        	KeyGenerator keyGenerator = KeyGenerator.getInstance(algorithm);
        	Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        	
        	SecretKey secretKey = null;
        	
        	//DirectByteBuffer supertempbuffer = new DirectByteBuffer(data.getBuffer(DirectByteBuffer.AL_MSG_BT_PAYLOAD));
        	byte[] tempdata = new byte[data.remaining(DirectByteBuffer.SS_MSG)];
        	if (!(peer.checkT1Piecekey(request.getPieceNumber()))){
        		
        		Debug.out("TCHAIN: Generating new key. happens before selecting nexthop");
            	keyGenerator.init(128);
            	secretKey = keyGenerator.generateKey();
            	
            	data.get(DirectByteBuffer.SS_MSG, tempdata);
            	data.position(DirectByteBuffer.AL_MSG_BT_PAYLOAD, 0);
            	Debug.out("P" + data.position(DirectByteBuffer.SS_MSG) 
            			+ "L" + data.limit(DirectByteBuffer.SS_MSG) + 
            			"C" + data.capacity(DirectByteBuffer.SS_MSG));
            	
        	}
        	else {
        		Debug.out("TCHAIN: Using old key. happens before selecting nexthop");
            	
        		byte[] oldkey = peer.getT1Piecekey(request.getPieceNumber());
        		secretKey = new SecretKeySpec(oldkey, 0, oldkey.length, "AES");
        		Debug.out("Old key " + oldkey.length + " : " + Arrays.toString(oldkey));
        	}
        	//Debug.out("Before encryption data bytes array length: " + tempdata.length);
    		
        	cipher.init(Cipher.ENCRYPT_MODE, secretKey);
    		byte[] encryptedByte = cipher.doFinal(tempdata);
    		byte[] keybytes = secretKey.getEncoded();
    		
    		//Debug.out("Encrypted data bytes array length: " + encryptedByte.length);
    		DirectByteBuffer encdata = new DirectByteBuffer(ByteBuffer.wrap(encryptedByte));
    		Base64.Encoder encoder = Base64.getEncoder();
    		
    		//Debug.out("Added key " + keybytes.length + ": " + Arrays.toString(keybytes));
        	/*
        	 * ByteBuffer buffer = ByteBuffer.allocateDirect(10000);
        	 * buffer.flip();
        	 * byte[] data = new byte[buffer.remaining()];
        	 * buffer.get(data);
        	 * Here you hava data populated inside data[] array
        	 * String token = new String(data);

        	 */
        	
    		//String encryptedText = encoder.encodeToString(encryptedByte);
         
        
        Debug.out("TCHAIN: Encoding data done");
        int abc = peer.doT1Piecekeyjob(request.getPieceNumber(), keybytes);
        PEPeer nextpe = null;
        ArrayList<PEPeer> somearr = peer.doT1RandomNexthop();
        if (somearr != null){
        	nextpe = somearr.get(0);
        	
        }
        
        byte[] nexthop = null;
        int keyrequested = -4;
        byte[] keyrequestedfrom = nothing;
        
        if (nextpe == null){
        	Debug.out("TCHAIN: NOT SELECTED FOR THIS PIECE");
        	nexthop = nothing;
        	encdata = data;
        	//Debug.out("P" + data.position(DirectByteBuffer.SS_MSG) 
        	//		+ "L" + data.limit(DirectByteBuffer.SS_MSG) + 
        	//		"C" + data.capacity(DirectByteBuffer.SS_MSG));
        	//Debug.out("------???---");
        	//Debug.out("P" + encdata.position(DirectByteBuffer.SS_MSG) 
        	//		+ "L" + encdata.limit(DirectByteBuffer.SS_MSG) + 
        	//		"C" + encdata.capacity(DirectByteBuffer.SS_MSG));
        }
        else {
        	nexthop = nextpe.getId();
        	Debug.out("Tchain in action for piece no" + request.getPieceNumber() + nexthop.toString());
	        int def = peer.doT1Translatorjob(request.getPieceNumber(), nexthop);
        }
	        //peer.doT2Reciprocation()
	        ArrayList<byte[]> recarr = peer.doT2Reciprocation();
	        
	        if (recarr != null){
	        	keyrequested = ByteBuffer.wrap(recarr.remove(0)).getInt();
	            keyrequestedfrom = recarr.remove(0);
	            Debug.out("TCHAIN: Doing reciprocation for " + keyrequested + PeerClassifier.getPrintablePeerID(keyrequestedfrom)); 
	        	}
            	
        
        //encdata.position(DirectByteBuffer.SS_MSG, 0);
        //Debug.out("P" + encdata.position(DirectByteBuffer.SS_MSG) 
    	//		+ "L" + encdata.limit(DirectByteBuffer.SS_MSG) + 
    	//		"C" + encdata.capacity(DirectByteBuffer.SS_MSG));
        Debug.out("Re,aimimg im buffer: " + encdata.remaining(DirectByteBuffer.SS_MSG));
        BTPiece msg = new BTPiece( request.getPieceNumber(), request.getOffset(), keyrequested, keyrequestedfrom, nexthop, encdata,piece_version );
        Debug.out(request.getPieceNumber() + " : "+ request.getOffset() + " : " + msg.getPieceData().position(DirectByteBuffer.SS_MSG) +": -" + msg.getPieceData().limit(DirectByteBuffer.SS_MSG) +": -"+ msg.getPieceData().capacity
        		(DirectByteBuffer.SS_MSG) +": -"+ msg.getKeyreq() + "nxthop and krfrom sent" + PeerClassifier.getPrintablePeerID(msg.getKeyreqfrom()));
      	
        // BTPiece msg = new BTPiece( request.getPieceNumber(), request.getOffset(), data, piece_version );
        queued_messages.put( msg, request );

        outgoing_message_queue.addMessage( msg, true );    
      }catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e){
      	
      }finally{
      	lock_mon.exit();
      }

      outgoing_message_queue.doListenerNotifications();
    }
    
    public void 
    readFailed( 
    	DiskManagerReadRequest 	request, 
  		Throwable		 		cause )
    {
        try{
          	lock_mon.enter();

          	if( !loading_messages.contains( request ) || destroyed ) { //was canceled
          	  return;
          	}
          	loading_messages.remove( request );
        }finally{
          	lock_mon.exit();
        }
        
      	peer.sendRejectRequest( request );
    }
    
    public int
    getPriority()
    {
    	return( -1 );
    }
	public void 
	requestExecuted(long bytes) 
	{
		adapter.diskRequestCompleted( bytes );							
	}
  };
  
  
  private final OutgoingMessageQueue.MessageQueueListener sent_message_listener = new OutgoingMessageQueue.MessageQueueListener() {
    public boolean messageAdded( Message message ) {   return true;   }
    
    public void messageSent( Message message ) {
      if( message.getID().equals( BTMessage.ID_BT_PIECE ) ) {
        try{
          lock_mon.enter();

          	// due to timing issues we can get in here with a message already removed
          
          queued_messages.remove( message );
   
        }finally{
          lock_mon.exit();
        }
        
        /*
    	if ( peer.getIp().equals( "64.71.5.2" )){
    		
    		outgoing_message_queue.setTrace( true );
    		
    		// BTPiece p = (BTPiece)message;
    		
    		// TimeFormatter.milliTrace( "obt sent: " + p.getPieceNumber() + "/" + p.getPieceOffset());
    	}
   		*/
        
        doReadAheadLoads();
      }
    }
    public void messageQueued( Message message ) {/*nothing*/}
    public void messageRemoved( Message message ) {/*nothing*/}
    public void protocolBytesSent( int byte_count ) {/*ignore*/}
    public void dataBytesSent( int byte_count ) {/*ignore*/}
    public void flush(){}
  };
  
 
  
  /**
   * Register a new piece data request.
   * @param piece_number
   * @param piece_offset
   * @param length
   */
  public boolean addPieceRequest( int piece_number, int piece_offset, int length ) {
    if( destroyed )  return( false );
         
    DiskManagerReadRequest dmr = peer.getManager().getDiskManager().createReadRequest( piece_number, piece_offset, length );

    try{
      lock_mon.enter();
         
      requests.addLast( dmr );
      
    }finally{
      lock_mon.exit();
    }
    
    doReadAheadLoads();
    
    return( true );
  }
  
  
  /**
   * Remove an outstanding piece data request.
   * @param piece_number
   * @param piece_offset
   * @param length
   */
  public void removePieceRequest( int piece_number, int piece_offset, int length ) {
  	if( destroyed )  return;
  	
    DiskManagerReadRequest dmr = peer.getManager().getDiskManager().createReadRequest( piece_number, piece_offset, length );
    
    boolean	inform_rejected = false;
    
    try{
      lock_mon.enter();
    
      if( requests.contains( dmr ) ) {
        requests.remove( dmr );
        inform_rejected = true;
        return;
      }
      
      if( loading_messages.contains( dmr ) ) {
        loading_messages.remove( dmr );
        inform_rejected = true;
        return;
      }
      
      
      for( Iterator i = queued_messages.entrySet().iterator(); i.hasNext(); ) {
        Map.Entry entry = (Map.Entry)i.next();
        if( entry.getValue().equals( dmr ) ) {  //it's already been queued
          BTPiece msg = (BTPiece)entry.getKey();
          if( outgoing_message_queue.removeMessage( msg, true ) ) {
        	inform_rejected = true;
            i.remove();
          }
          break;  //do manual listener notify
        }
      }
    }finally{
    	
      lock_mon.exit();
      
      if ( inform_rejected ){
 
   		  peer.sendRejectRequest( dmr );
      }
    }
    
    outgoing_message_queue.doListenerNotifications();
  }
  
  
  
  /**
   * Remove all outstanding piece data requests.
   */
  public void removeAllPieceRequests() {
  	if( destroyed )  return;
  	
  	List<DiskManagerReadRequest> removed = new ArrayList<DiskManagerReadRequest>();
  	
    try{
      lock_mon.enter();
      
      // removed this trace as Alon can't remember why the trace is here anyway and as far as I can
      // see there's nothing to stop a piece being delivered to transport and removed from
      // the message queue before we're notified of this and thus it is entirely possible that
      // our view of queued messages is lagging.
      // String before_trace = outgoing_message_queue.getQueueTrace();     
      /*
      int num_queued = queued_messages.size();
      int num_removed = 0;
      
      for( Iterator i = queued_messages.keySet().iterator(); i.hasNext(); ) {
        BTPiece msg = (BTPiece)i.next();
        if( outgoing_message_queue.removeMessage( msg, true ) ) {
          i.remove();
          num_removed++;
        }
      }
      
      if( num_removed < num_queued -2 ) {
        Debug.out( "num_removed[" +num_removed+ "] < num_queued[" +num_queued+ "]:\nBEFORE:\n" +before_trace+ "\nAFTER:\n" +outgoing_message_queue.getQueueTrace() );		
      }
      */
      
     
      for( Iterator<BTPiece> i = queued_messages.keySet().iterator(); i.hasNext(); ) {
          BTPiece msg = i.next();
          if (outgoing_message_queue.removeMessage( msg, true )){
        	  removed.add( queued_messages.get( msg ));
          }
      }
      
      queued_messages.clear();	// this replaces stuff above 
      
      removed.addAll( requests );
      
      requests.clear();
      
      removed.addAll( loading_messages );
      
      loading_messages.clear();
    }
    finally{
      lock_mon.exit();
    }
    
    for ( DiskManagerReadRequest request: removed ){
    	
    	 peer.sendRejectRequest( request );
    }
    
    outgoing_message_queue.doListenerNotifications();
  }
      

  
  public void setRequestReadAhead( int num_to_read_ahead ) {
    request_read_ahead = num_to_read_ahead;
  }
  
  
  
  public void destroy() {
    try{
      lock_mon.enter();
  
      removeAllPieceRequests();
      
      queued_messages.clear();
      
      destroyed = true;
      
      outgoing_message_queue.cancelQueueListener(sent_message_listener);
    }
    finally{
      lock_mon.exit();
    }
  }
  
  
  private void doReadAheadLoads() {
  	List	to_submit = null;
  	try{
  		lock_mon.enter();
		
  		while( loading_messages.size() + queued_messages.size() < request_read_ahead && !requests.isEmpty() && !destroyed ) {
  			DiskManagerReadRequest dmr = (DiskManagerReadRequest)requests.removeFirst();
  			loading_messages.add( dmr );  			
  			if( to_submit == null )  to_submit = new ArrayList();
  			to_submit.add( dmr );
  		}	
    }finally{
    	lock_mon.exit();
    }
    
    /*
	if ( peer.getIp().equals( "64.71.5.2")){

		TimeFormatter.milliTrace( "obt read_ahead: -> " + (to_submit==null?0:to_submit.size()) + 
				" [lo=" + loading_messages.size() + ",qm=" + queued_messages.size() + ",re=" + requests.size() + ",rl=" + request_read_ahead + "]");		
	}
	*/
    
    if ( to_submit != null ){
    	for (int i=0;i<to_submit.size();i++){
    		peer.getManager().getAdapter().enqueueReadRequest( peer, (DiskManagerReadRequest)to_submit.get(i), read_req_listener );
    	}
    }
  }

  /**
	 * Get a list of piece numbers being requested
	 *  
	 * @return list of Long values
	 */
	public int[] getRequestedPieceNumbers() {
		if( destroyed )  return new int[0];
		
		/** Cheap hack to reduce (but not remove all) the # of duplicate entries */
		int iLastNumber = -1;
		int pos = 0;		
		int[] pieceNumbers;
	
		try {
			lock_mon.enter();			

			// allocate max size needed (we'll shrink it later)
			pieceNumbers = new int[queued_messages.size()	+ loading_messages.size() + requests.size()];

			for (Iterator iter = queued_messages.keySet().iterator(); iter.hasNext();) {
				BTPiece msg = (BTPiece) iter.next();
				if (iLastNumber != msg.getPieceNumber()) {
					iLastNumber = msg.getPieceNumber();
					pieceNumbers[pos++] = iLastNumber;
				}
			}

			for (Iterator iter = loading_messages.iterator(); iter.hasNext();) {
				DiskManagerReadRequest dmr = (DiskManagerReadRequest) iter.next();
				if (iLastNumber != dmr.getPieceNumber()) {
					iLastNumber = dmr.getPieceNumber();
					pieceNumbers[pos++] = iLastNumber;
				}
			}

			for (Iterator iter = requests.iterator(); iter.hasNext();) {
				DiskManagerReadRequest dmr = (DiskManagerReadRequest) iter.next();
				if (iLastNumber != dmr.getPieceNumber()) {
					iLastNumber = dmr.getPieceNumber();
					pieceNumbers[pos++] = iLastNumber;
				}
			}
			
		} finally {
			lock_mon.exit();
		}

		int[] trimmed = new int[pos];
		System.arraycopy(pieceNumbers, 0, trimmed, 0, pos);

		return trimmed;		
	}
	
	public int
	getRequestCount()
	{
		return( queued_messages.size()	+ loading_messages.size() + requests.size());
	}
	
	public boolean
	isStalledPendingLoad()
	{
		return( queued_messages.size() == 0 && loading_messages.size() > 0 );
	}
}
