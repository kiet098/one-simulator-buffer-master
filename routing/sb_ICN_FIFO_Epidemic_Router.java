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
public class sb_ICN_FIFO_Epidemic_Router extends ActiveRouter {


	public static final String Epi_NS = "sb_ICN_FIFO_Epidemic_Router";
	public static final String dataBufferSize_str = "dataBufferSize";
	public static final String requestBufferSize_str = "requestBufferSize";
	
	// BE CAREFUL WHEN SETTING BUFFER SIZES FOR DATA AND REQUEST 
	// WE DON't WANT THESE 2 VALUES TO HAVE A SUM GREATER THAN BUFFER SIZE DECLARED FOR MESSAGE ROUTER through Group.BufferSize of the setting file
 
	private int dataBufferSize;
	private int requestBufferSize;

	//initialise data and request buffers 
	private ArrayList<Message> dataBuffer = new ArrayList<Message>(); 
	private ArrayList<Message> requestBuffer = new ArrayList<Message>();	
	
	//property key for tracking transferring host ID of requests	
	public static final String TRANSFERRING_HOST_PROPERTY = "request_transferring_host_ID";
	
	//property key for returning reponse to the transferring node that just sends request
	public static final String TO_TRANSFERRING_HOST_PROPERTY = "request_transferring_host_ID_to_response";

	/**
	 * Constructor. Creates a new request response router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public sb_ICN_FIFO_Epidemic_Router(Settings s) {
		super(s);
		//TODO: read&use request response router specific settings (if any)
		Settings Epi_setting = new Settings(Epi_NS);
		dataBufferSize = Epi_setting.getInt(dataBufferSize_str);
		requestBufferSize = Epi_setting.getInt(requestBufferSize_str);
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected sb_ICN_FIFO_Epidemic_Router(sb_ICN_FIFO_Epidemic_Router r) {
		super(r);
		//TODO: copy request response settings here (if any)
		this.dataBufferSize = r.dataBufferSize;
		this.requestBufferSize = r.requestBufferSize;
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
		if (m.getProperty("type").equals("request")) {
			String idToFind = (String) m.getProperty("target");
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
			//check if request buffer is full
			FIFO_packet_manage(requestBuffer,m,requestBufferSize);
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
		
		ArrayList<Message> transferBuffer = new ArrayList<Message>();
		// ArrayList<Message> responseBuffer = new ArrayList<Message>();
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
				
				if(m.getProperty(TRANSFERRING_HOST_PROPERTY) == null){
					m.addProperty(TRANSFERRING_HOST_PROPERTY, getHost()); //tracking ID of current transferring host to return response later on 
				}
				else{
					m.updateProperty(TRANSFERRING_HOST_PROPERTY, getHost()); //tracking ID of current transferring host to return response later on
				}
				
				transferBuffer.add(m);
			}
		}
		this.tryMessagesToConnections(transferBuffer, this.getConnections());
		
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
	public sb_ICN_FIFO_Epidemic_Router replicate() {
		return new sb_ICN_FIFO_Epidemic_Router(this);
	}

}