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
public class sb_ICN_DirectDelivery_Router extends ActiveRouter {
	
	public static final String DD_NS = "sb_ICN_DirectDelivery_Router";
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
	public sb_ICN_DirectDelivery_Router(Settings s) {
		super(s);
		//TODO: read&use request response router specific settings (if any)
		Settings DD_setting = new Settings(DD_NS);
		dataBufferSize = DD_setting.getInt(dataBufferSize_str);
		requestBufferSize = DD_setting.getInt(requestBufferSize_str);
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected sb_ICN_DirectDelivery_Router(sb_ICN_DirectDelivery_Router r) {
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

			}
		// remove interest ("request") packet from buffer regardless of having requested message or not to ensure DirectDelivery
		// the receiver node won't carry the "request" packet
			removeFromMessages(id);
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
		ArrayList<Message> transferBuffer = new ArrayList<Message>();
		// ArrayList<Message> responseBuffer = new ArrayList<Message>();
		
		//sending requests around 
		for (Message m : this.getMessageCollection()) {
			String type = (String) m.getProperty("type");
			//alteration			
			/*if (m.isResponse()) {
				transferBuffer.add(0,m);
			} else
			*/
			//end of alteration
			if (type.equals("request")) {
				transferBuffer.add(m);
			}
		}
		this.tryMessagesToConnections(transferBuffer, this.getConnections());
		//Direct Delivery of data - added alteration 
		/* try messages that could be delivered to final recipient */
		if (exchangeDeliverableMessages() != null) {
			return;
		}//end of alteration 
		
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
	public sb_ICN_DirectDelivery_Router replicate() {
		return new sb_ICN_DirectDelivery_Router(this);
	}

}