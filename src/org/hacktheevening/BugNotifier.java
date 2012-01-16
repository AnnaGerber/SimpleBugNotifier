package org.hacktheevening;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.Tweet;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.internal.http.BASE64Encoder;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;

public class BugNotifier {
		public static String [] bugAddresses = {"5002", "5004", "5005"};
		public BugNotifier() {
			try {
				
				// set up the message sources
				
				// Edge RSS
				Thread rssThread = new Thread(new EdgeRSSRunnable());
				rssThread.start();
				
				// Sensor data from Pachube
				//Thread sensorThread = new Thread(new SensorRunnable());
				//sensorThread.start();
				
				// Twitter
				Thread twitterThread = new Thread(new TwitterRunnable());
				twitterThread.start();
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		class TwitterRunnable implements Runnable {
			private String postUrl;
			Twitter twitter;
			public TwitterRunnable(){
				try{
				this.twitter = new TwitterFactory().getInstance();
					Properties props = new Properties();
					FileInputStream propsFileInputStream = new FileInputStream(new File("bugNotifier.properties"));
					props.load(propsFileInputStream);
					String xbeeServer = props.getProperty("xbeeServer");
					this.postUrl = "http://" + xbeeServer + "/elwire.html?";
				} catch (Exception e){
					e.printStackTrace();
				}
			}
			public void run(){
				try {
				// Notify on start (just so we see something happen immediately)
				for (String bugAddress : BugNotifier.bugAddresses){
        			URL requestUrl = new URL(this.postUrl + "address=" + bugAddress + "&pattern=1");
        			this.notifyBug(requestUrl);
        		}
				} catch (Exception ex){
					System.err.println("Problem notifying bugs: " + ex.getMessage());
				}
				Date lastRun = new Date();
				while(true){
				    Date thisRun = new Date();
			        try {
			            QueryResult result = twitter.search(
			            		new Query("slqedge"));
			            List<Tweet> tweets = result.getTweets();
			            int numNotifications = 0;
			            for (Tweet tweet : tweets) {
			            	if (tweet.getCreatedAt().after(lastRun)){
			            		// all bugs light up when a tweet is received
			            		numNotifications++;
			            		System.out.println("New tweet @" + tweet.getFromUser() + " - " + tweet.getText());
			            		for (String bugAddress : BugNotifier.bugAddresses){
			            			this.notifyBug(new URL(this.postUrl + "address=" + bugAddress + "&pattern=1"));
			            		}
			            	} else {
			            		System.out.println("old tweet from " + tweet.getFromUser());
			            	}
			            	if (numNotifications > 1){
				        		// sleep for 5 seconds so that previous sound can finish playing
				        		Thread.sleep(5000);
				        	}
			            }
			            lastRun = thisRun;
			        } catch (Exception te) {
			            te.printStackTrace();
			            System.out.println("Failed to search tweets: " + te.getMessage());
			        }
			        System.out.println("done processing Twitter");
			        try {
			        	// sleep for 5 minutes
						Thread.sleep(5 * 60 * 1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
					
				}
			}
			private void notifyBug(URL postUrl) {
				BufferedReader in = null;
				try{
					HttpURLConnection conn = (HttpURLConnection) postUrl.openConnection();
					in = new BufferedReader(
	                        new InputStreamReader(
	                        conn.getInputStream()));
					String inputLine;
					while ((inputLine = in.readLine()) != null) 
					    System.out.println(inputLine);
					in.close();
				} catch (Exception e){
					System.err.println("Problem in notify " + e.getMessage());
					if (in != null){
						try{
							in.close();
						} catch (Exception e1){
						}
					}
				}
			}
		}
		/**
		 * SensorRunnable gets sensor events from Pachube and posts to Arduino bugs
		 */
		class SensorRunnable implements Runnable {
			private URL postUrl;
			private String userDetails;
			private URL feedUrl;
			public SensorRunnable(){
				try{
					Properties props = new Properties();
					FileInputStream propsFileInputStream = new FileInputStream(new File("bugNotifier.properties"));
					props.load(propsFileInputStream);
					feedUrl  = new URL(props.getProperty("sensorFeedUrl"));
					userDetails = props.getProperty("sensorUserDetails");
					
				} catch (Exception e){
					e.printStackTrace();
				}
			}
			public void run(){
				Date lastRun = new Date();
				try {
					// pachube needs username/password, encode them
					HttpURLConnection conn = (HttpURLConnection)feedUrl.openConnection();
					String encoding = BASE64Encoder.encode(userDetails.getBytes());
					conn.setRequestProperty  ("Authorization", "Basic " + encoding);
					conn.connect();
				    InputStreamReader in = new InputStreamReader((InputStream) conn.getContent());
				    BufferedReader buff = new BufferedReader(in);
				    StringBuffer text = new StringBuffer();
					  String line;
					    do {
					      line = buff.readLine();
					      if (line != null){
					    	  text.append(line + "\n");
					      }
					    } while (line != null);
					
					System.out.println("got json " + text.toString());
					JSONObject obj=(JSONObject)JSONValue.parse(text.toString());
					obj.get("datastreams");
					
					
					System.out.println("done");

				} catch (Exception e){
					e.printStackTrace();
				}
			}
			private void notify(String message) throws Exception {
				HttpURLConnection conn = (HttpURLConnection) postUrl.openConnection();
				conn.setDoOutput(true);
				conn.setRequestMethod("POST");
				OutputStreamWriter out = new OutputStreamWriter(conn.getOutputStream());
				out.write(message);
				out.close();
			}
		}
		/**
		 * EdgeRSSRunnable polls the RSS feed for the Edge and sends notifications 
		 *
		 */
		class EdgeRSSRunnable implements Runnable {
			private URL feedUrl;
			private String elwireURL;
			private String ledURL;
			private String speakerURL;
			public EdgeRSSRunnable() {
				URL feedUrl = null;
				try{
					Properties props = new Properties();
					FileInputStream propsFileInputStream = new FileInputStream(new File("bugNotifier.properties"));
					props.load(propsFileInputStream);
					feedUrl  = new URL(props.getProperty("edgeFeedUrl"));
					String xbeeServer = props.getProperty("xbeeServer");
					this.elwireURL = "http://" + xbeeServer + "/elwire.html?";
					this.ledURL = "http://" + xbeeServer + "/led.html?";
					this.speakerURL = "http://" + xbeeServer + "/speaker.html?";
				} catch (Exception e){
					e.printStackTrace();
				}
				this.feedUrl = feedUrl;
			}
			
			@Override
			public void run() {
				
			    if (this.feedUrl == null){
			    	  return;
			    }
				XmlReader reader = null;
				Date lastRun = new Date();
				
				while(true){
				    try {
				     Date thisRun = new Date();
				     reader = new XmlReader(feedUrl);
				     SyndFeed feed = new SyndFeedInput().build(reader);
				     int numNotifications = 0;
				     for (Iterator i =  feed.getEntries().iterator(); i.hasNext();) {
				        SyndEntry entry = (SyndEntry) i.next();
				        if (entry.getPublishedDate().after(lastRun)){
				        	String title = entry.getTitle();
				        	System.out.println(title);
				        	numNotifications++;
				        	String message = "rss";
				        	// what type of event is it?
				        	if (title.contains("became a registered member")){
				        		message = "registered";
				        	} else if (title.contains("unlocked")){
				        		message = "unlocked";
				        	} else if (title.contains("joined the group")){
				        		message = "joined";
				        	} else if (title.contains("posted an update")){
				        		message = "update";
				        	} else if (title.contains("are now friends")){
				        		message="friended";
				        	} else if (title.contains("likes")){
				        		message = "liked";
				        	} else if (title.contains("commented on")){
				        		message="commented";
				        	} else if (title.contains("wrote a new blog post")){
				        		message="blogged";
				        	} 
				        	this.mapMessage(message);
				        	
				        	if (numNotifications > 1){
				        		// sleep for 5 seconds so that previous sound can finish playing
				        		Thread.sleep(5000);
				        	}
	
				        }
		             }
				     
				     lastRun = thisRun;
				     
				    } catch (Exception e){
				    	e.printStackTrace();
			        } finally {
			            if (reader != null){
			                try {
								reader.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
			        	}
					}
			        System.out.println("done processing Edge RSS");
			        try {
			        	// sleep for 1 minute
						Thread.sleep(1 * 60 * 1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
					
				}
			}
			
			private void mapMessage(String message) throws Exception {
				Random rand = new Random();
				String randomBug = BugNotifier.bugAddresses[rand.nextInt(5)];
				if (message.equals("registered")){
					this.notifyBug(new URL(this.elwireURL + "address=" + randomBug + "&pattern=1"));
					this.notifyBug(new URL(this.speakerURL + "address=" + randomBug + "&pattern=1"));
				} else if (message.equals("joined")){
					this.notifyBug(new URL(this.elwireURL + "address=" + randomBug + "&pattern=2"));
					this.notifyBug(new URL(this.speakerURL + "address=" + randomBug + "&pattern=2"));
				} else if (message.equals("unlocked")){
					this.notifyBug(new URL(this.ledURL + "address=" + randomBug + "&pattern=2"));
					this.notifyBug(new URL(this.speakerURL + "address=" + randomBug + "&pattern=1"));
				}  else if (message.equals("update")){
					this.notifyBug(new URL(this.elwireURL + "address=" + randomBug + "&pattern=2"));
				} else if (message.equals("blogged")){
					this.notifyBug(new URL(this.elwireURL + "address=" + randomBug + "&pattern=1"));
				} else if (message.equals("friended")){
					this.notifyBug(new URL(this.ledURL + "address=" + randomBug + "&pattern=1"));
				} else if (message.equals("commented")){
					this.notifyBug(new URL(this.speakerURL + "address=" + randomBug + "&pattern=1"));
				} else if (message.equals("liked")){
					this.notifyBug(new URL(this.speakerURL + "address=" + randomBug + "&pattern=2"));
				}  else { // default : there should always be a message, but just in case
					this.notifyBug(new URL(this.ledURL + "address=" + randomBug + "&pattern=2"));
				}
			}
			
			private void notifyBug(URL postUrl) {
				BufferedReader in = null;
				try{
					HttpURLConnection conn = (HttpURLConnection) postUrl.openConnection();
					in = new BufferedReader(
	                        new InputStreamReader(
	                        conn.getInputStream()));
					String inputLine;
					while ((inputLine = in.readLine()) != null) 
					    System.out.println(inputLine);
					in.close();
				} catch (Exception e){
					System.err.println("Problem in notify " + e.getMessage());
					if (in != null){
						try{
							in.close();
						} catch (Exception e1){
						}
					}
				}
			}
		}
		
		

		
		/**
		 * @param args
		 */
		public static void main(String[] args) {
				new BugNotifier();
		}

	

}
