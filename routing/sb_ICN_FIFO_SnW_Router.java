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
public class sb_ICN_FIFO_SnW_Router extends ActiveRouter {
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES = "nrofCopies";
	/** identifier for the binary-mode setting ({@value})*/ 
	public static final String BINARY_MODE = "binaryMode";
	/** SprayAndWait router's settings name space ({@value})*/ 
	public static final String ICN_SPRAYANDWAIT_NS = "sb_ICN_FIFO_SnW_Router";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = ICN_SPRAYANDWAIT_NS + "." +
		"copies";
	
	protected int initialNrofCopies;
	protected boolean isBinary;

	
	//property key for tracking transferring host ID of requests	
	public static final String TRANSFERRING_HOST_PROPERTY = "request_transferring_host_ID";
	
	//property key for returning reponse to the transferring node that just sends request
	public static final String TO_TRANSFERRING_HOST_PROPERTY = "request_transferring_host_ID_to_response";	
	
	
	//FIFO buffer management 
	public static final String dataBufferSize_str = "dataBufferSize";
	public static final String requestBufferSize_str = "requestBufferSize";
	
	// BE CAREFUL WHEN SETTING BUFFER SIZES FOR DATA AND REQUEST 
	// WE DON't WANT THESE 2 VALUES TO HAVE A SUM GREATER THAN BUFFER SIZE DECLARED FOR MESSAGE ROUTER through Group.BufferSize of the setting file
 
	private int dataBufferSize;
	private int requestBufferSize;

	//initialise data and request buffers 
	private ArrayList<Message> dataBuffer = new ArrayList<Message>(); 
	private ArrayList<Message> requestBuffer = new ArrayList<Message>();		

	/**
	 * Constructor. Creates a new request response router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public sb_ICN_FIFO_SnW_Router(Settings s) {
		super(s);
		//TODO: read&use request response router specific settings (if any)
		Settings snwSettings = new Settings(ICN_SPRAYANDWAIT_NS);
		initialNrofCopies = snwSettings.getInt(NROF_COPIES);
		isBinary = snwSettings.getBoolean(BINARY_MODE);
		dataBufferSize = snwSettings.getInt(dataBufferSize_str);
		requestBufferSize = snwSettings.getInt(requestBufferSize_str);
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected sb_ICN_FIFO_SnW_Router(sb_ICN_FIFO_SnW_Router r) {
		super(r);
		//TODO: copy request response settings here (if any)
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;
		this.dataBufferSize = r.dataBufferSize;
		this.requestBufferSize = r.requestBufferSize;		
	}

	@Override
	public int receiveMessage( Message m, DTNHost from){
		return super.receiveMessage(m,from);
	}

	// from perspective of receiving host
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message m = super.messageTransferred(id, from);
		
		if(m.getProperty("type").equals("data")){
			//check if data buffer is full 
			FIFO_packet_manage(dataBuffer,m,dataBufferSize);
		}
		
		// only check buffer messages if received message is an interest packet
		else if (m.getProperty("type").equals("request")) {
			String idToFind = (String) m.getProperty("target");
			
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);		
			assert nrofCopies != null : "Not a SnW message: " + m;
			
			
			if (hasMessage(idToFind)) {
				Message match = getMessage(idToFind);
				// set request packet
				match.setRequest(m);
				// set the destination packet of this information to the source of request packet
				match.setTo(m.getFrom());

				//prepare response to be returned to the transferring host of request packet 
				if(match.getProperty(TO_TRANSFERRING_HOST_PROPERTY) == null){
					match.addProperty(TO_TRANSFERRING_HOST_PROPERTY, m.getProperty(TRANSFERRING_HOST_PROPERTY));
				}
				else{ //update transferring DTNHost for response when receive new request  
					match.updateProperty(TO_TRANSFERRING_HOST_PROPERTY, m.getProperty(TRANSFERRING_HOST_PROPERTY));
				}

				// remove interest packet from buffer because it has served its purpose
				removeFromMessages(id);
			}
			else{				
				//check if nrofCopies is already 1 
				if(nrofCopies > 1){
					
					//check if request buffer is full
					//put it here to avoid extra removal if nrofCopies == 1 at the beginning 
					FIFO_packet_manage(requestBuffer,m,requestBufferSize);		
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
				else if(nrofCopies == 1){
					//Direct Delivery mechanism of final SnW copy of request 
					// remove interest packet from buffer anyway in case of nrofCopies = 1 already for request 
					removeFromMessages(id);					
				}
			}
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
		 * 1) transfer only response and request packets
		 * 2) prioritise response packets to be sent through the same
		 * connection where it received the interest packets
		 */

		//send response to connection that just sends matched request
		List<Connection> connectionList = this.getConnections();
		for(Connection c: connectionList){
			for(Message m: this.getMessageCollection()){
				if(m.isResponse()){
					if(c.getOtherNode(getHost()) == m.getProperty(TO_TRANSFERRING_HOST_PROPERTY)){
						int retVal = startTransfer(m, c); //transfer the response to request transferring node 
					}
				}
			}
		}

		List<Message> unsorted_list = new ArrayList<Message>();
		List<Message> unsorted_finalRequestCopy = new ArrayList<Message>();
		
		for (Message m : this.getMessageCollection()) {
			String type = (String) m.getProperty("type");
			
			if(type.equals("data")){
				//check for self-generated data by this node 
				if(!dataBuffer.contains(m)){
					FIFO_packet_manage(dataBuffer,m,dataBufferSize); // FIFO manage to add this node's self-generated data to dataBuffer
				}
			}
			
			else if (type.equals("request")) {
				//check for self-generated request
				if(!requestBuffer.contains(m)){
					FIFO_packet_manage(requestBuffer,m,requestBufferSize); // FIFO manage to add this node's self-generated request to requestBuffer 
				}				
				
				//check if property is added
				if(m.getProperty(MSG_COUNT_PROPERTY) == null){
					m.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
				}
				
				if(m.getProperty(TRANSFERRING_HOST_PROPERTY) == null){
					m.addProperty(TRANSFERRING_HOST_PROPERTY, getHost()); //tracking ID of current transferring host to return response later on 
				}
				else{
					m.updateProperty(TRANSFERRING_HOST_PROPERTY, getHost()); //tracking ID of current transferring host to return response later on
				}
				
				//create a list of "request" this router is still carrying and nrofcopies >1
				Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
				assert nrofCopies != null : "SnW message " + m + " didn't have " + 
				"nrof copies property!";		

				if(nrofCopies >1){
					unsorted_list.add(m);					
				}
				else if(nrofCopies == 1){
					unsorted_finalRequestCopy.add(m);
				}		
			}
		}
		
		//create a list of SAW "request" with copies left to distribute
		@SuppressWarnings(value = "unchecked")
		List<Message> transferBuffer = sortByQueueMode(unsorted_list);
		
		this.tryMessagesToConnections(transferBuffer, this.getConnections());
		
		//Direct Delivery mechanism for sending final SnW copy of request 
		@SuppressWarnings(value = "unchecked")
		List<Message> sorted_finalRequestCopy = sortByQueueMode(unsorted_finalRequestCopy);		
		this.tryMessagesToConnections(sorted_finalRequestCopy, this.getConnections());		
		
		//mechanism to ensure no excess of packets in types' buffers 
		/* do a copy to avoid concurrent modification exceptions */
		ArrayList<Message> temp = new ArrayList<Message>(this.getMessageCollection());
		ArrayList<Message> dataToBeRemoved = new ArrayList<Message>();
		ArrayList<Message> requestToBeRemoved = new ArrayList<Message>();
		
		for(Message m: dataBuffer){
			if(!temp.contains(m)){
				dataToBeRemoved.add(m);
			}
		}
		dataBuffer.removeAll(dataToBeRemoved);
		for(Message m: requestBuffer){
			if(!temp.contains(m)){
				requestToBeRemoved.add(m);
			}
		}
		requestBuffer.removeAll(requestToBeRemoved);
	}

	//function to manage packets in FIFO way when buffer is full 
	protected void FIFO_packet_manage(ArrayList<Message> bufferList, Message packetToAdd, int bufferLimit){
		
		if(bufferList.size() < bufferLimit){
				bufferList.add(packetToAdd);//new packet add to the end of the list 
		}
		else{
			//buffer is full
			//return oldest element to remove; 
			Message packetToRemove = bufferList.get(0); //oldest element is at the beginning of buffer 
			
			bufferList.add(packetToAdd);
			
			if(bufferList.remove(packetToRemove)) {  //remove oldest packet from buffer list 
				deleteMessage(packetToRemove.getId(), true);
				//boolean haha = bufferList.remove(packetToRemove);
			}
						
		}		
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

		//check if it is request packet as only "request" packet has MSG_COUNT_PROPERTY property 
		String type = (String) msg.getProperty("type");
		if(type.equals("request")){
			if (msg == null) { // message has been dropped from the buffer after..
				return; // ..start of transfer -> no need to reduce amount of copies
			}
			
			/* reduce the amount of copies left */
			nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
			
			//if trying to introduce the mechanism to prevent reducing nrofCopies = 1 to even lower 
			//the program throws null pointer exception???
			//if(nrofCopies >1){
				if (isBinary) { 
					nrofCopies /= 2;
				}
				else {
					nrofCopies--;
				}
				msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
			//}
			
		}
	}


	//leave this alone
	//FIFO buffer management 
	@Override
	protected Message getNextMessageToRemove(boolean excludeMsgBeingSent) {
		/*Collection<Message> messages = this.getMessageCollection();
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
		*/
		return null;
	}
	
	@Override
	public sb_ICN_FIFO_SnW_Router replicate() {
		return new sb_ICN_FIFO_SnW_Router(this);
	}

}