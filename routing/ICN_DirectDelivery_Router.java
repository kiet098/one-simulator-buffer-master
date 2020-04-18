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
import core.MessageListener;

import util.Tuple;

/**
 * request response router to handle requests and return message packet
 */
public class ICN_DirectDelivery_Router extends ActiveRouter {
	
	/**
	 * Constructor. Creates a new request response router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public ICN_DirectDelivery_Router(Settings s) {
		super(s);
		//TODO: read&use request response router specific settings (if any)
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected ICN_DirectDelivery_Router(ICN_DirectDelivery_Router r) {
		super(r);
		//TODO: copy request response settings here (if any)
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
				
				//notify about successful interest/request 
				this.deliveredMessages.put(id, m);
				for (MessageListener ml : this.mListeners) {
					ml.messageTransferred(m, from, getHost(), true);
				}
				//*/

			}
		// remove interest ("request") packet from buffer regardless of having requested message or not to ensure DirectDelivery
		// the receiver node won't carry the "request" packet
			removeFromMessages(id);
		}
		
		/*remove unwanted response 
		if(m.isResponse()){
			if(m.getTo() != getHost() && getHost()!=null){
				String requestIdtoFind = m.getRequest().getId();
				if(hasMessage(requestIdtoFind)==false){
					removeFromMessages(id);
				}
			}
		}
		*/
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
		
	}

	@Override
	public ICN_DirectDelivery_Router replicate() {
		return new ICN_DirectDelivery_Router(this);
	}

}