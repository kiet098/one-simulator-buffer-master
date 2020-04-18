/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */

package applications;

import java.util.Random;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

import core.Application;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import core.SimScenario;
import core.World;

public class GeneratorApplication extends Application {
	/** Run in passive mode - don't generate content but respond */
	public static final String CONTENT_PASSIVE = "passive";
	/** Content host range */
	public static final String CONTENT_PROPORTION = "proportion";
	/** Content generation interval */
	public static final String CONTENT_INTERVAL = "interval";
	/** Content range - inclusive lower, exclusive upper */
	public static final String CONTENT_RANGE = "range";
	/** Destination address range - inclusive lower, exclusive upper */
	public static final String CONTENT_DEST_RANGE = "destinationRange";
	/** Content size range - inclusive lower, exclusive upper */
	public static final String CONTENT_SIZE_RANGE = "sizeRange";
	/** Size of the content message */
	public static final String CONTENT_TYPE = "contentType";

    /** Application ID */
	public static final String APP_ID = "fi.tkk.netlab.GeneratorApplication";

    // Private vars
    private double			lastCreation = 0;
    private double			interval = 500;
	private int				contentMin = 0;
	private int				contentMax = 1;
	private int				propMin = 0;
	private int				propMax = 1;
	private int				destMin = 0;
	private int				destMax = 1;
	private int				sizeMin = 1;
	private int				sizeMax = 10;
    private boolean 		passive = false;
	private List<String> 	contentType = new ArrayList();

    /** 
	 * Creates a new generator application with the given settings.
	 * 
	 * @param s	Settings to use for initializing the application.
	 */
    public GeneratorApplication(Settings s) {
        if (s.contains(CONTENT_PASSIVE)){
			this.passive = s.getBoolean(CONTENT_PASSIVE);
		}
		if (s.contains(CONTENT_PROPORTION)){
			int[] prop = s.getCsvInts(CONTENT_PROPORTION,2);
			this.propMin = prop[0];
			this.propMax = prop[1];
		}
		if (s.contains(CONTENT_INTERVAL)){
			this.interval = s.getDouble(CONTENT_INTERVAL);
		}
		if (s.contains(CONTENT_TYPE)){
			this.contentType = Arrays.asList(s.getSetting(CONTENT_TYPE).split("\\|"));
		}
		if (s.contains(CONTENT_RANGE)) {
			int[] range = s.getCsvInts(CONTENT_RANGE,2);
			this.contentMin = range[0];
			this.contentMax = range[1];
		}
		if (s.contains(CONTENT_SIZE_RANGE)) {
			int[] size = s.getCsvInts(CONTENT_SIZE_RANGE,2);
			this.sizeMin = size[0];
			this.sizeMax = size[1];
		}
		if (s.contains(CONTENT_DEST_RANGE)){
			int[] destination = s.getCsvInts(CONTENT_DEST_RANGE,2);
			this.destMin = destination[0];
			this.destMax = destination[1];
		}

		super.setAppID(APP_ID);
	}

    /** 
	 * Copy-constructor
	 * 
	 * @param a
	 */
	public GeneratorApplication(GeneratorApplication a) {
		super(a);
		this.lastCreation = a.getLastCreation();
		this.propMin = a.getPropMin();
		this.propMax = a.getPropMax();
		this.interval = a.getInterval();
		this.passive = a.isPassive();
		this.contentMax = a.getContentMax();
		this.contentMin = a.getContentMin();
		this.destMax = a.getDestMax();
		this.destMin = a.getDestMin();
		this.sizeMax = a.getSizeMax();
		this.sizeMin = a.getSizeMin();
		this.contentType = a.getContentType();
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
		return new GeneratorApplication(this);
	}

	public int randomSize() {
		int randomInt = 0;
		Random rng = new Random();
		if (sizeMax == sizeMin) randomInt = sizeMin;
		randomInt = sizeMin + rng.nextInt(sizeMax - sizeMin);

		return randomInt;
	}

	public String randomContent() {
		int randomInt = 0;
		Random rng = new Random();
		if (contentMax == contentMin) randomInt = contentMin;
		randomInt = contentMin + rng.nextInt(contentMax - contentMin);

		return "M" + randomInt;
	}

	public int randomDestination() {
		int randomInt = 0;
		Random rng = new Random();
		if (destMax == destMin) randomInt = destMin;
		randomInt = destMin + rng.nextInt(destMax - destMin);

		return randomInt;
	}

	/*
	 * Checks if host has application to actively data packets
	 */
	public boolean hostActive(DTNHost host) {
		return (host.getAddress() < getPropMax() && host.getAddress() >= getPropMin());
	}

    /** 
	 * Generate a data packet.
	 * 
	 * @param host to which the application instance is attached
	 */
	@Override
	public void update(DTNHost host) {
		if (this.passive) return;
		double curTime = SimClock.getTime();
		if (curTime - this.lastCreation >= this.interval && hostActive(host)) {
			// Message m = new Message(host, null, getId(host), randomSize());
			Message m = new Message(host, null, randomContent(), randomSize());
			m.addProperty("type", "data");

			// declare random destinations and target packets for interest packets
			//DTNHost randDest = SimScenario.getInstance().getWorld().getNodeByAddress(randomDestination());
			//m.setTo(randDest);
			//m.setTo(null);
			
			// send in all the possible content types to be randomized later
			int randomIndex = (int)(Math.random() * this.contentType.size());
			m.addProperty("contenttype", this.contentType.get(randomIndex));
			m.setAppID(APP_ID);
			host.createNewMessage(m);

			// set location of data creation
			m.addProperty("initiallocation", host.getLocation());
			
			// Call listeners
			super.sendEventToListeners("SentContent", null, host);
			
			this.lastCreation = curTime;
		}
	}

	/**
	 * lastCreation
	 */
	public double getLastCreation() { return lastCreation; }
	public void setLastCreation(double lastCreation) { this.lastCreation = lastCreation; }

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
	 * contentMin and contentMax
	 */
	public int getContentMin() { return contentMin; }
	public void setContentMin(int contentMin) { this.contentMin = contentMin; }
	public int getContentMax() { return contentMax; }
	public void setContentMax(int contentMax) { this.contentMax = contentMax; }

	/**
	 * destMin and destMax
	 */
	public int getDestMin() { return destMin; }
	public void setDestMin(int destMin) { this.destMin = destMin; }
	public int getDestMax() { return destMax; }
	public void setDestMax(int destMax) { this.destMax = destMax; }

	/**
	 * propMin and propMax
	 */
	public int getPropMin() { return propMin; }
	public void setPropMin(int propMin) { this.propMin = propMin; }
	public int getPropMax() { return propMax; }
	public void setPropMax(int propMax) { this.propMax = propMax; }

	/**
	 * sizeMin and sizeMax
	 */
	public int getSizeMin() { return sizeMin; }
	public void setSizeMin(int sizeMin) { this.sizeMin = sizeMin; }
	public int getSizeMax() { return sizeMax; }
	public void setSizeMax(int sizeMax) { this.sizeMax = sizeMax; }

	/**
	 * @return the contentType
	 */
	public List<String> getContentType() {
		return contentType;
	}

	/**
	 * @param host the host generating the content
	 * @return the id
	 */
	public String getId(DTNHost host) {
		return "M" + host.getAddress();
	}

}