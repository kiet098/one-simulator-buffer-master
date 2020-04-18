/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.List;
import java.util.ArrayList;

import core.Settings;
import core.DTNHost;
import core.SimClock;
import core.Message;
import core.MessageListener;

/**
 * Reports information about all created messages. Messages created during
 * the warm up period are ignored.
 * For output syntax, see {@link #HEADER}.
 */
public class ICNWholeEvaluationReport extends Report implements MessageListener {
	private float excess;
	private float total;

	private Settings s = new Settings("Group");
	private int nrofHosts = s.getInt("nrofHosts");
	private int[][] successRate = new int[nrofHosts][3];
	private int[][] responseMatch = new int[nrofHosts][2];
	private int[][] droppedPackets = new int[nrofHosts][2];

	// for tracking purpose
	private Message currMessage = null;
	private DTNHost currFrom = null;
	private DTNHost currTo = null;

	/**
	 * Constructor.
	 */
	public ICNWholeEvaluationReport() {
		init();
	}
	
	@Override
	public void init() {
		super.init();
		this.excess = 0;
		this.total = 0;
	}
	
	
	public void newMessage(Message m) {
		if (isWarmup()) {
			addWarmupID(m.getId());
		}
	}

	public void messageTransferred(Message m, DTNHost f, DTNHost t, boolean firstDelivery) {
		String type = (String) m.getProperty("type");

		if (!isWarmupID(m.getId())) {
			

			if (type.equals("request")) {
				
				if(!firstDelivery){
					//m.getFrom() == f && 
					if(!(currMessage == m || currFrom == f || currTo == t)){
					currMessage = m;
					currFrom = f;
					currTo = t;

					successRate[m.getFrom().getAddress()][0]++;
					
					}
				}
				else if (firstDelivery){
					successRate[f.getAddress()][2]++;
					//successRate[f.getAddress()][0]++;
				}
			}

			if (m.isResponse()) {
				if (firstDelivery) {
					successRate[t.getAddress()][1]++;
					responseMatch[t.getAddress()][1]++;
				} else {
					responseMatch[t.getAddress()][0]++;
				}
			}
			// efficiency
			if (type.equals("data")) {
				// total transfers
				this.total++;
				// transfers that are not to the destination
				if (!firstDelivery) {
					this.excess++;
				}
			}
		}
	}
	public void messageDeleted(Message m, DTNHost where, boolean dropped) {
		// program message drop report here
		if (dropped) {
			if (m.isResponse()) {
				droppedPackets[where.getAddress()][0]++;
			} else {
				droppedPackets[where.getAddress()][1]++;
			}
		}
	}

	public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {}
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {}

	@Override
	public void done() {
		float total = 0;
		float total_matched = 0;
		float interest_dropped = 0;
		float data_dropped = 0;
		float count = 0;
		float count_matched = 0;
		
		//overall efficiency 
		float total_successful_response = 0;
		float total_interest_sent = 0;
		
		//interest success 
		float total_successful_interest = 0;
		
		for (int i = 0; i < successRate.length; i++) {
			float transmit = successRate[i][0];
			float receive = successRate[i][1];
			float response = responseMatch[i][0];
			float matched = responseMatch[i][1];
			
			//total successful interests
			total_successful_interest += successRate[i][2] ;
			
			//overall efficiency 
			total_successful_response = total_successful_response + receive;
			total_interest_sent = total_interest_sent + transmit;
			
			if (transmit != 0) {
				count++;
				total += (receive/transmit);
				
			}
			if (response != 0) {
				count_matched++;
				total_matched += (matched/response);
			}
			data_dropped = droppedPackets[i][0];
			interest_dropped += droppedPackets[i][1];
		}

		String overall_efficiency = String.format("%.4f",(total_successful_response/total_interest_sent)*100);
		//String success_rate_string = String.format("%.4f",(total*100/count)); //it turns out that count = total # of transmission hosts
		
		String total_interest_sent_str = String.format("%.4f",total_interest_sent);
		String total_successful_interest_str = String.format("%.4f",total_successful_interest);
		String interest_success_rate_str = String.format("%.4f",total_successful_interest*100/total_interest_sent);
		
		String total_response_sent_str = String.format("%.4f",this.total); 
		String response_efficiency = String.format("%.4f", (this.total-this.excess)*100/this.total);
		//String overhead = String.format("%.4f", (this.excess)/(this.total-this.excess));

		String total_successful_response_sent_str = String.format("%.4f",total_successful_response);
		
		String dropped_interest_string = String.format("%.4f",interest_dropped*100/nrofHosts);
		String dropped_data_string = String.format("%.4f",data_dropped*100/nrofHosts);
		//write("=========== efficiency ==========");
		//write("Excess: " + this.excess);
		//write("Total: " + this.total);
		//write("Efficiency (%): " + percentage);

		//write("=========== dropped packets ==========");
		//write("Average Dropped Interests" + interest_dropped*100/nrofHosts);
		//write("Average Dropped Data: " + data_dropped*100/nrofHosts);
		
		String bigOutputString = getScenarioName()
		+"ZZ"+overall_efficiency
		+"ZZ"+interest_success_rate_str
		+"ZZ"+total_successful_interest_str
		+"ZZ"+total_interest_sent_str
		+"ZZ"+response_efficiency
		+"ZZ"+total_successful_response_sent_str
		+"ZZ"+total_response_sent_str
		+"ZZ"+dropped_interest_string
		+"ZZ"+dropped_data_string;
		write(bigOutputString); 
		//in order, separated by ZZ
		//routing methods and evaluation independent variables
		//average success rate of interest (request) packets
		//efficiency (%)	
		//overhead (%)
		//average dropped interests
		//average dropped data
		//*/
		super.done();
	}
}
