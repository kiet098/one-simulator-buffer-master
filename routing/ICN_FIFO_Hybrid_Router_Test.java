/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

import core.Settings;
import core.Message;
import core.DTNHost;
import core.Connection;

import util.Tuple;

/**
 * request response router to handle requests and return message packet
 */
 
//ICN Hybrid: send request like epidemic and data like SnW  
public class ICN_FIFO_Hybrid_Router_Test extends ActiveRouter {
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES = "nrofCopies";
	/** identifier for the binary-mode setting ({@value})*/ 
	public static final String BINARY_MODE = "binaryMode";
	/** SprayAndWait router's settings name space ({@value})*/ 
	public static final String ICN_HYBRID_NS = "ICN_FIFO_Hybrid_Router_Test";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = ICN_HYBRID_NS + "." +
		"copies";
	
	protected int initialNrofCopies;
	protected boolean isBinary;	
	
	/**
	 * Constructor. Creates a new request response router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public ICN_FIFO_Hybrid_Router_Test(Settings s) {
		super(s);
		//TODO: read&use request response router specific settings (if any)
		Settings hybridSettings = new Settings(ICN_HYBRID_NS);
		initialNrofCopies = hybridSettings.getInt(NROF_COPIES);
		isBinary = hybridSettings.getBoolean(BINARY_MODE);
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected ICN_FIFO_Hybrid_Router_Test(ICN_FIFO_Hybrid_Router_Test r) {
		super(r);
		//TODO: copy request response settings here (if any)
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;
	}

	@Override
	public int receiveMessage( Message m, DTNHost from){
		return super.receiveMessage(m,from);
	}

	// from perspective of receiving host
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message m = super.messageTransferred(id, from);
		// only check buffer messages if received message is an interest packet
		if (m.getProperty("type").equals("request")) {
			String idToFind = (String) m.getProperty("target");
			if (hasMessage(idToFind)) {
				Message match = getMessage(idToFind);
				// set request packet
				match.setRequest(m);
				// set the destination packet of this information to the source of request packet
				match.setTo(m.getFrom());
				// remove interest packet from buffer because it has served its purpose
				removeFromMessages(id);
			}
		}

		//when received message is a non-response data
		String type = (String) m.getProperty("type");
		if(m.isResponse()== false && type.equals("data")){
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);		
			assert nrofCopies != null : "Not a SnW message: " + m;
			
			if(isBinary){
				//in binary SnW receiving node gets ceil(n/2) copies
				nrofCopies = (int)Math.ceil(nrofCopies/2.0);				
			}
			else{
				//in standard SnW receving node gets only single copy
				nrofCopies = 1;
			}
			m.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);				
		}
		
		return m;
	}
	
	// from perspective of transferring host
	@Override
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}
		/**
		 * 1) transfer response, data and request packets
		 * 2) prioritise response packets to be sent through the same
		 * connection where it received the interest packets
		 */
		
		//ArrayList<Message> transferBuffer = new ArrayList<Message>();
		List<Message> responseBuffer = new ArrayList<Message>();
		List<Message> unsorted_list = new ArrayList<Message>();
		List<Message> requestBuffer = new ArrayList<Message>();
		
		for (Message m : this.getMessageCollection()) {
			String type = (String) m.getProperty("type");

			//"response" packets  
			if (m.isResponse()== true) {

				//check if property is added
				if(m.getProperty(MSG_COUNT_PROPERTY) == null){
					m.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
				}
					
				responseBuffer.add(0,m);
			}
			if(type.equals("data") && m.isResponse()== false){
				
				//create a list of "data" this router is still carrying and nrofcopies >1
				if(m.getProperty(MSG_COUNT_PROPERTY) != null){
					Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
					assert nrofCopies != null : "SnW message " + m + " didn't have " + 
					"nrof copies property!";		

					if(nrofCopies >1){
						unsorted_list.add(0,m);
					}				
				}
			}
			if (type.equals("request")){
				requestBuffer.add(m);
			}			
		}
		
		//create a list of SAW "data" with copies left to distribute
		@SuppressWarnings(value = "unchecked")
		List<Message> transferBuffer = sortByQueueMode(unsorted_list);
		
		//add the request buffer at the bottom of transfer buffer 
		transferBuffer.addAll(requestBuffer);
		
		//add the response buffer at the beginning
		transferBuffer.addAll(0,responseBuffer);
		
		this.tryMessagesToConnections(transferBuffer, this.getConnections());
	}
	

	/**
	 * Called just before a transfer is finalized (by 
	 * {@link ActiveRouter#update()}).
	 * Reduces the number of copies we have left for a message. 
	 * In binary Spray and Wait, sending host is left with floor(n/2) copies,
	 * but in standard mode, nrof copies left is reduced by one. 
	 */

	@Override
	protected void transferDone(Connection con) {
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		//check if it is response packet
		String type = (String) msg.getProperty("type");
		if(type.equals("data")){
			if (msg == null) { // message has been dropped from the buffer after..
					return; // ..start of transfer -> no need to reduce amount of copies
				}	
			if(msg.isResponse()== true){
				
				//make "response" data become "nonresponse" again to be sent as SnW "data" 
				msg.setRequest(null);
				msg.setTo(null);
				/* reduce the amount of copies left */
				nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
				assert nrofCopies != null : "SnW message " + msg + " didn't have " + "nrof copies property!";		

				if (isBinary) { 
					nrofCopies /= 2;
				}
				else {
					nrofCopies--;
				}
				msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
			}
			
			//SnW "data", i.e non-reponse SnW "data" after "response" data is sent, SnW property is retained and become non-response again 
			if(msg.isResponse()== false){
				if(msg.getProperty(MSG_COUNT_PROPERTY) != null){
					/* reduce the amount of copies left */
					nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
					assert nrofCopies != null : "SnW message " + msg + " didn't have " + "nrof copies property!";		

					if (isBinary) { 
						nrofCopies /= 2;
					}
					else {
						nrofCopies--;
					}
					msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);						
				}
			}
		}		
	}

	
	//leave this alone
	//FIFO buffer management
	@Override
	protected Message getNextMessageToRemove(boolean excludeMsgBeingSent) {
		Collection<Message> messages = this.getMessageCollection();
		Message oldest = null;
		for (Message m : messages) {
			if (excludeMsgBeingSent && isSending(m.getId())) {
				continue; // skip the message(s) that router is sending
			}
			if (oldest == null) {
				oldest = m;
			} else if (oldest.getReceiveTime() > m.getReceiveTime()) {
				oldest = m;
			}
		}
		
		return oldest;
	}
	
	@Override
	public ICN_FIFO_Hybrid_Router_Test replicate() {
		return new ICN_FIFO_Hybrid_Router_Test(this);
	}

}