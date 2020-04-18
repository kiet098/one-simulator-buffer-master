/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.List;
import java.util.Collection;

import core.DTNHost;
import core.Settings;
import core.Message;
import core.SimClock;
import core.UpdateListener;

/**
 * Report for message content in each node
 * Messages that were created during the warm up period are ignored
 */
public class MessageContentReport extends Report implements UpdateListener {
    /**
	 * end time of simulation
	 */
    private double endTime = 10000;

	/**
	 * Constructor.
	 */
	public MessageContentReport() {
		super();
	}

    public void updated(List<DTNHost> hosts) {
		if (SimClock.getTime() > endTime) {
			printLine(hosts);
		}
	}
	
	/**
	 * Prints a snapshot of the message content through all nodes
	 * @param hosts The list of hosts in the simulation
	 */
	private void printLine(List<DTNHost> hosts) {
		String output = "SimTime: " + format(SimClock.getTime()) + "\n";

		for (DTNHost h : hosts) {
			Collection<Message> tmp = h.getMessageCollection();
			output += tmp + "\n";
		}
		write(output);
	}
	
}
