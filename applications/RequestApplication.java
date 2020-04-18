/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */

package applications;

import java.util.Random;

import core.Application;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.SimScenario;
import core.World;

public class RequestApplication extends Application {
    /** Run in passive mode - don't generate request but respond */
	public static final String REQUEST_PASSIVE = "passive";
	/** Request generation interval */
	public static final String REQUEST_INTERVAL = "interval";
	/** Request host range */
	public static final String REQUEST_PROPORTION = "proportion";
	/** Destination address range - inclusive lower, exclusive upper */
	public static final String REQUEST_DEST_RANGE = "destinationRange";
	/** Target range - inclusive lower, exclusive upper */
	public static final String TARGET_RANGE = "targetRange";
	/** Priority range - inclusive lower, exclusive upper */
	public static final String REQUEST_PRIORITY_RANGE = "priorityRange";
    /** Size of the request message */
	public static final String REQUEST_SIZE = "requestSize";

    /** Application ID */
	public static final String APP_ID = "fi.tkk.netlab.RequestApplication";

    // Private vars
    private double	lastRequest = 0;
    private double	interval = 500;
	private int		propMin = 0;
	private int		propMax = 1;
	private int		destMin = 0;
	private int		destMax = 1;
	private int		targetMin = 0;
	private int		targetMax = 1;
	private int		priorMin = 0;
	private int		priorMax = 1;
    private boolean passive = false;
    private int		requestSize = 1;

    /** 
	 * Creates a new request application with the given settings.
	 * 
	 * @param s	Settings to use for initializing the application.
	 */
    public RequestApplication(Settings s) {
        if (s.contains(REQUEST_PASSIVE)){
			this.passive = s.getBoolean(REQUEST_PASSIVE);
		}
		if (s.contains(REQUEST_INTERVAL)){
			this.interval = s.getDouble(REQUEST_INTERVAL);
		}
		if (s.contains(REQUEST_PROPORTION)){
			int[] prop = s.getCsvInts(REQUEST_PROPORTION,2);
			this.propMin = prop[0];
			this.propMax = prop[1];
		}
		if (s.contains(REQUEST_DEST_RANGE)){
			int[] destination = s.getCsvInts(REQUEST_DEST_RANGE,2);
			this.destMin = destination[0];
			this.destMax = destination[1];
		}
		if (s.contains(TARGET_RANGE)){
			int[] target = s.getCsvInts(TARGET_RANGE,2);
			this.targetMin = target[0];
			this.targetMax = target[1];
		}
		if (s.contains(REQUEST_PRIORITY_RANGE)){
			int[] priority = s.getCsvInts(REQUEST_PRIORITY_RANGE,2);
			this.priorMin = priority[0];
			this.priorMax = priority[1];
		}
		if (s.contains(REQUEST_SIZE)){
			this.requestSize = s.getInt(REQUEST_SIZE);
		}

		super.setAppID(APP_ID);
	}

    /** 
	 * Copy-constructor
	 * 
	 * @param a
	 */
	public RequestApplication(RequestApplication a) {
		super(a);
		this.lastRequest = a.getLastRequest();
		this.interval = a.getInterval();
		this.propMin = a.getPropMin();
		this.propMax = a.getPropMax();
		this.passive = a.isPassive();
		this.destMax = a.getDestMax();
		this.destMin = a.getDestMin();
		this.targetMax = a.getTargetMax();
		this.targetMin = a.getTargetMin();
		this.priorMax = a.getPriorMax();
		this.priorMin = a.getPriorMin();
		this.requestSize = a.getRequestSize();
	}

    /** 
	 * Handles an incoming message.
	 * 
	 * @param msg	message received by the router
	 * @param host	host to which the application instance is attached
	 */
	@Override
	public Message handle(Message msg, DTNHost host) {
		return msg;
	}

    @Override
	public Application replicate() {
		return new RequestApplication(this);
	}

	public int randomInteger() {
		int randomInt = 0;
		Random rng = new Random();
		if (destMax == destMin) randomInt = destMin;
		randomInt = destMin + rng.nextInt(destMax - destMin);

		return randomInt;
	}

	public String randomTarget() {
		int randomInt = 0;
		Random rng = new Random();
		if (targetMax == targetMin) randomInt = targetMin;
		randomInt = targetMin + rng.nextInt(targetMax - targetMin);

		return "M" + randomInt;
	}

	public int randomPriority() {
		int randomInt = 0;
		Random rng = new Random();
		if (priorMax == priorMin) randomInt = priorMin;
		randomInt = priorMin + rng.nextInt(priorMax - priorMin);

		return randomInt;
	}

	/*
	 * Checks if host has application to actively request packets
	 */
	public boolean hostActive(DTNHost host) {
		return (host.getAddress() < getPropMax() &&
				host.getAddress() >= getPropMin());
	}

    /** 
	 * Generate a request.
	 * @param host to which the application instance is attached
	 */
    @Override
	public void update(DTNHost host) {
        if (this.passive) return;
		double curTime = SimClock.getTime();
		if (curTime - this.lastRequest >= this.interval && hostActive(host)) {
			Message m = new Message(host, null, getId(host), getRequestSize());
			m.addProperty("type", "request");
			m.setAppID(APP_ID);

			// declare random destination
			//Jing's code
			//DTNHost randDest = SimScenario.getInstance().getWorld().getNodeByAddress(randomInteger());
			//m.setTo(randDest);
			//I change this to below:
			//m.setTo(null);
			
			// declare random target packet
			m.addProperty("target", randomTarget());
			// declare random priority level
			m.addProperty("priority", randomPriority());
			host.createNewMessage(m);

			// set location of data creation
			m.addProperty("initiallocation", host.getLocation());
			
			// Call listeners
			super.sendEventToListeners("SentRequest", null, host);
			
			this.lastRequest = curTime;
		}
    }

    /**
	 * lastRequest
	 */
	public double getLastRequest() { return lastRequest; }
	public void setLastRequest(double lastRequest) { this.lastRequest = lastRequest; }

    /**
	 * interval
	 */
	public double getInterval() { return interval; }
	public void setInterval(double interval) { this.interval = interval; }

	/**
	 * passive
	 */
	public boolean isPassive() { return passive; }
	public void setPassive(boolean passive) { this.passive = passive; }

	/**
	 * propMin and propMax
	 */
	public int getPropMin() { return propMin; }
	public void setPropMin(int propMin) { this.propMin = propMin; }
	public int getPropMax() { return propMax; }
	public void setPropMax(int propMax) { this.propMax = propMax; }

	/**
	 * destMin and destMax
	 */
	public int getDestMin() { return destMin; }
	public void setDestMin(int destMin) { this.destMin = destMin; }
	public int getDestMax() { return destMax; }
	public void setDestMax(int destMax) { this.destMax = destMax; }

	/**
	 * targetMin and TargetMax
	 */
	public int getTargetMin() { return targetMin; }
	public void setTargetMin(int targetMin) { this.targetMin = targetMin; }
	public int getTargetMax() { return targetMax; }
	public void setTargetMax(int targetMax) { this.targetMax = targetMax; }

	/**
	 * priorMin and priorMax
	 */
	public int getPriorMin() { return priorMin; }
	public void setPriorMin(int priorMin) { this.priorMin = priorMin; }
	public int getPriorMax() { return priorMax; }
	public void setPriorMax(int priorMax) { this.priorMax = priorMax; }

    /**
	 * requestSize
	 */
	public int getRequestSize() { return requestSize; }
	public void setRequestSize(int requestSize) { this.requestSize = requestSize; }

	/**
	 * @param host the host sending the request
	 * @return the contentSize
	 */
	// TODO need new way to name interest packet
	public String getId(DTNHost host) {
		return "I" + host.getAddress();
	}

}