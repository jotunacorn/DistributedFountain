/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.kth.chord.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.kth.chord.FountainCoder.FountainDecoder;
import se.kth.chord.FountainCoder.FountainEncoder;
import se.kth.chord.component.init.NodeInit;
import se.kth.chord.msg.Pong;
import se.kth.chord.msg.RetrieveFile;
import se.kth.chord.msg.Status;
import se.kth.chord.msg.net.*;
import se.kth.chord.msg.parentport.NewParentAlert;
import se.kth.chord.msg.parentport.ParentPort;
import se.kth.chord.node.DataBlock;
import se.kth.chord.node.NodeHandler;
import se.kth.chord.timeout.*;
import se.sics.kompics.*;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;



public class NodeComp extends ComponentDefinition {

    //  ----- VARIABLES AND SETTINGS USED FOR THE SWIM SYSTEM ----
    private static final boolean ENABLE_LOGGING = false;
    private static final int PING_TIMEOUT = 2000; //Time until a node will be K-pinged
    private static final int SUSPECTED_TIMEOUT = 2000; //Time until it's declared suspected
    private static final int DEAD_TIMEOUT = 2000; //Time until it's declared dead
    private static final int AGGREGATOR_TIMEOUT = 1000; //Delay between sending info to aggregator
    private static final int K = 4; //K value, how many nodes we K-ping if we suspect a node.
    public static final int PIGGYBACK_MESSAGE_SIZE = 9999999; //How many nodes piggybacked in each pong.
    public static final int LAMBDA = 3; //How many times the node change is piggybacked. Lambda * log(n)
    private static final int BYTES_TO_READ = 32 * 1024 * 1024;
    public static final Logger log = LoggerFactory.getLogger(NodeComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    private Positive<ParentPort> parentPort = requires(ParentPort.class);
    private final NatedAddress selfAddress;
    private final NatedAddress aggregatorAddress;
    private UUID pingTimeoutId;
    private UUID statusTimeoutId;
    private Random rand;
    //Various counters
    private int sentPings = 0;
    private int receivedPings = 0;
    private int incarnationCounter = 0;
    private int sentStatuses = 0;
    
    private final String ORIGINAL_FILE = "C:\\Users\\Douglas\\Downloads\\OpenELEC-Generic.x86_64-5.95.5.img";

    //The NodeHandler is holding all information about nodes in the system.
    //It provides an API to get and set nodes to the different lists in a consistent way.
    private NodeHandler nodeHandler;

    //Collections holding information about what pings we sent.
    private List<Integer> sentPingNrs;
    private Map<Integer, NatedAddress> sentIndirectPings;
    private Map<Integer, Integer> kPingNrToPingNrMapping;

    //DATA STORAGE STUFF
    // TODO(Douglas): BUG! Cannot store multiple files with same filename. Hash the names?
    HashMap<String, Set<DataBlock>> storedData;
    
    // List to keep track of nodes 
    private volatile List<NatedAddress> nodeWaitList = new ArrayList<>();
    
    // Map of blocks used to decode file
    private volatile Map<String, Set<DataBlock>> fileDataBlocks = new HashMap<>();

    public NodeComp(NodeInit init) {
        if (ENABLE_LOGGING) {
            log.info("{} initiating...", init.selfAddress);
        }

        selfAddress = init.selfAddress;
        aggregatorAddress = init.aggregatorAddress;

        this.rand = new Random(init.seed);

        nodeHandler = new NodeHandler(selfAddress, init.seed);

        sentPingNrs = new ArrayList<>();
        sentIndirectPings = new HashMap<>();
        kPingNrToPingNrMapping = new HashMap<>();

        // Add all bootstrap nodes to our alive list.
        for (NatedAddress address : init.bootstrapNodes) {
            nodeHandler.addAlive(address, 0);
        }

        if (ENABLE_LOGGING) {
            nodeHandler.printAliveNodes();
        }

        storedData = new HashMap<>();

        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handlePing, network);
        subscribe(handlePong, network);
        subscribe(handleAlive, network);
        subscribe(handleNetKPing, network);
        subscribe(handleNetKPong, network);
        subscribe(handleNewParent, parentPort);
        subscribe(handlePingTimeout, timer);
        subscribe(handleStatusTimeout, timer);
        subscribe(handlePongTimeout, timer);
        subscribe(handleSuspectedTimeout, timer);
        subscribe(handleDeadTimeout, timer);
        
        // FS subs
        subscribe(handleAddFile, network);
        subscribe(handleRemoveFile, network);
        subscribe(handleRetrieveFile, network);
        subscribe(handleAddOriginalFile, network);
        subscribe(handleRemoveOriginalFile, network);
        subscribe(handleRetrieveOriginalFile, network);
        subscribe(handleSendDataBlocks, network);
        subscribe(handleAddTimeout, timer);
    }

    /**
     * Handler for starting the component.
     * Will schedule the periodic pings and status messages.
     */
    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            if (ENABLE_LOGGING) {
                log.info("{} starting...", new Object[]{selfAddress.getId()});
            }

            schedulePeriodicPing();
            schedulePeriodicStatus();
            if (selfAddress.getId() == 12)
            	scheduleAddOrigFile();
        }

    };

    /**
     * Handler for stopping the component.
     */
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            if (ENABLE_LOGGING) {
                log.info("{} stopping...", new Object[]{selfAddress.getId()});
            }

            if (pingTimeoutId != null) {
                cancelPeriodicPing();
            }

            if (statusTimeoutId != null) {
                cancelPeriodicStatus();
            }
        }

    };

    /**
     * Handler for receiving pong messages.
     * Will add sender node as alive and add all nodes in the piggybacked node data to our node data.
     */
    private Handler<NetPong> handlePong = new Handler<NetPong>() {

        @Override
        public void handle(NetPong event) {
            if (ENABLE_LOGGING) {
                log.info("{} received pong nr {} from:{}", new Object[]{selfAddress.getId(), event.getContent().getPingNr(), event.getHeader().getSource()});
            }

            //If the ping number of the pong was in the list of sent pings, it was a regular ping.
            boolean wasRegularPing = sentPingNrs.remove(Integer.valueOf(event.getContent().getPingNr()));
            if (wasRegularPing) {
                //Add all new nodes to our alive list, taking incarnation numbers into account.
                for (NatedAddress address : event.getContent().getNewNodes().keySet()) {
                    nodeHandler.addAlive(address, event.getContent().getNewNodes().get(address));
                }

                //Add all suspected nodes to our suspected list, taking incarnation numbers into account.
                for (NatedAddress address : event.getContent().getSuspectedNodes().keySet()) {
                    nodeHandler.addSuspected(address, event.getContent().getSuspectedNodes().get(address));
                }

                //Add all dead nodes to the dead list.
                for (NatedAddress address : event.getContent().getDeadNodes().keySet()) {
                    if (ENABLE_LOGGING) {
                        log.info("{} Declared node {} dead from pong", new Object[]{selfAddress.getId(), address});
                    }

                    nodeHandler.addDead(address, event.getContent().getDeadNodes().get(address));
                }

                //Add the node who sent the pong to the alive list.
                nodeHandler.addDefinatelyAlive(event.getSource(), event.getContent().getIncarnationCounter());

                //If we find ourself in the suspected list
                if (event.getContent().getSuspectedNodes().containsKey(selfAddress)) {
                    if (ENABLE_LOGGING) {
                        log.info("{} Found self in suspected list from node: {}", new Object[]{selfAddress.getId(), event.getSource()});
                    }

                    //Increase the incarnation number and send Alive messages to all alive nodes.
                    incarnationCounter++;

                    for (NatedAddress address : nodeHandler.getAliveNodes().keySet()) {
                        trigger(new NetAlive(selfAddress, address, incarnationCounter), network);
                    }
                }
            }
            //Otherwise, if not a regular ping it was a K-ping. Check if it is still in sent list.
            else if (sentIndirectPings.containsKey(event.getContent().getPingNr())) {
                if (ENABLE_LOGGING) {
                    log.info("{} forwarding KPing result for suspected node {} to: {}", new Object[]{selfAddress.getId(), event.getSource(), sentIndirectPings.get(event.getContent().getPingNr())});
                }

                //If this was a response to a k-ping, forward the result to the requester node.
                trigger(new NetKPong(selfAddress, sentIndirectPings.get(event.getContent().getPingNr()), event.getSource(), event.getContent().getIncarnationCounter(), kPingNrToPingNrMapping.get(event.getContent().getPingNr())), network);
                sentIndirectPings.remove(event.getContent().getPingNr());
                kPingNrToPingNrMapping.remove(event.getContent().getPingNr());
            }

            if (ENABLE_LOGGING) {
                nodeHandler.printAliveNodes();
            }
        }

    };

    /**
     * Handler for receiving ping messages.
     * Will add sender to alive list and send a pong response.
     */
    private Handler<NetPing> handlePing = new Handler<NetPing>() {

        @Override
        public void handle(NetPing event) {
            if (ENABLE_LOGGING) {
                log.info("{} received ping nr {} from:{}", new Object[]{selfAddress.getId(), event.getContent().getPingNr(), event.getHeader().getSource()});
            }

            receivedPings++;

            //Add the sender node to the alive list
            nodeHandler.addDefinatelyAlive(event.getSource(), event.getContent().getIncarnationCounter());

            if (ENABLE_LOGGING) {
                log.info("{} sending pong nr {} to :{}", new Object[]{selfAddress.getId(), event.getContent().getPingNr(), event.getSource()});
            }

            //Send a pong
            Pong pong = nodeHandler.getPong(event.getContent().getPingNr(), incarnationCounter);
            trigger(new NetPong(selfAddress, event.getSource(), pong), network);

            if (ENABLE_LOGGING) {
                nodeHandler.printAliveNodes();
            }
        }

    };

    /**
     * Handler for receiving new parent nodes from the NatTraversal component.
     * When NatTraversal component detects any of our parents died, it will provide us with new parent nodes.
     * The new nodes will be added to the parent list and we will inform the other nodes by
     * adding ourself as a new node.
     */
    private Handler<NewParentAlert> handleNewParent = new Handler<NewParentAlert>() {

        @Override
        public void handle(NewParentAlert event) {
            //If we get a new parent event, new parents from croupier, add them to self address
            //and then add the updated information to the alive list.
            selfAddress.getParents().clear();
            selfAddress.getParents().addAll(event.getParents());

            if (ENABLE_LOGGING) {
                log.info("{} New parents arrived: {}", new Object[]{selfAddress.getId(), event.getParents()});
            }

            incarnationCounter++;

            //Add self to the send buffer as a new node, so it will be propagated the next time someone ping us.
            nodeHandler.addNewNodeToSendBuffer(selfAddress, incarnationCounter);
        }

    };

    /**
     * Handler for receiving alive messages.
     * Will add the sender to our alive nodes.
     */
    private Handler<NetAlive> handleAlive = new Handler<NetAlive>() {

        @Override
        public void handle(NetAlive netAlive) {
            if (ENABLE_LOGGING) {
                log.info("{} Restored suspected node by alive message from: {}", new Object[]{selfAddress.getId(), netAlive.getSource()});
            }

            //Upon receiving an alive message, add the node to the alive list. Will remove node from suspected list.
            nodeHandler.addAlive(netAlive.getSource(), netAlive.getContent().getIncarnationCounter());
        }

    };

    /**
     * Handler for receiving K-ping messages.
     * Some node requests us to ping a node for them. Will send a ping to the requested node.
     */
    private Handler<NetKPing> handleNetKPing = new Handler<NetKPing>() {

        @Override
        public void handle(NetKPing netKPing) {
            if (ENABLE_LOGGING) {
                log.info("{} received KPing request for suspected node {}", new Object[]{selfAddress.getId(), netKPing.getContent().getAddressToPing()});
            }

            //When we get a K-ping request, send a ping to the node someone requests us to ping.
            trigger(new NetPing(selfAddress, netKPing.getContent().getAddressToPing(), sentPings, incarnationCounter), network);
            sentIndirectPings.put(sentPings, netKPing.getSource());
            kPingNrToPingNrMapping.put(sentPings, netKPing.getContent().getPingNr());
            sentPings++;
        }

    };

    /**
     * Handler for receiving K-ping response.
     * Received if K-ping succeeded, will then add the requested node to the alive list.
     */
    private Handler<NetKPong> handleNetKPong = new Handler<NetKPong>() {

        @Override
        public void handle(NetKPong netKPong) {
            if (ENABLE_LOGGING) {
                log.info("{} received KPong for suspected node {}", new Object[]{selfAddress.getId(), netKPong.getContent().getAddress()});
            }

            //When getting a k-ping response (K-pong) add the node to the alive list again.
            nodeHandler.addDefinatelyAlive(netKPong.getContent().getAddress(), netKPong.getContent().getIncarnationCounter());

            sentPingNrs.remove((Integer) netKPong.getContent().getPingNr());

            if (ENABLE_LOGGING) {
                nodeHandler.printAliveNodes();
            }
        }

    };

    /**
     * Handler for receiving ping timeout.
     * This is triggering periodically for us to send a ping message to a random alive node.
     */
    private Handler<PingTimeout> handlePingTimeout = new Handler<PingTimeout>() {

        @Override
        public void handle(PingTimeout event) {
            NatedAddress partnerAddress = nodeHandler.getRandomAliveNode();

            if (partnerAddress != null) {
                if (ENABLE_LOGGING) {
                    log.info("{} sending ping nr {} to partner:{}", new Object[]{selfAddress.getId(), sentPings, partnerAddress});
                }

                //Periodically send pings to a random alive node.
                trigger(new NetPing(selfAddress, partnerAddress, sentPings, incarnationCounter), network);

                //Start a timer for when the ping will timeout and we will suspect the node being dead.
                ScheduleTimeout scheduleTimeout = new ScheduleTimeout(PING_TIMEOUT);
                PongTimeout pongTimeout = new PongTimeout(scheduleTimeout, sentPings, partnerAddress);
                scheduleTimeout.setTimeoutEvent(pongTimeout);
                trigger(scheduleTimeout, timer);

                //Remember which pings we have sent by saving ping number.
                //Ping numbers will be included in the pong, so we can know which pong is
                //answering to which ping.
                sentPingNrs.add(sentPings);

                sentPings++;
            }
        }

    };

    /**
     * Handler for receiving status timeout.
     * When received a status message is sent to the aggregator component.
     * Status contains our alive, suspected and dead nodes.
     */
    private Handler<StatusTimeout> handleStatusTimeout = new Handler<StatusTimeout>() {

        @Override
        public void handle(StatusTimeout event) {
            if (ENABLE_LOGGING) {
                log.info("{} sending status nr:{} to aggregator:{}", new Object[]{selfAddress.getId(), sentStatuses, aggregatorAddress});
            }

            //Send a status with our alive, suspected and dead nodes to the aggregator component periodically.
            Map<NatedAddress, Integer> sendAliveNodes = new HashMap<>(nodeHandler.getAliveNodes());
            trigger(new NetStatus(selfAddress, aggregatorAddress, new Status(sentStatuses, receivedPings, sentPings, sendAliveNodes, nodeHandler.getSuspectedNodes(), nodeHandler.getDeadNodes())), network);

            sentStatuses++;
        }

    };

    /**
     * Handler for receiving pong timeout.
     * This is the timeout we scheduled when sending a ping.
     * If no response to the ping was received before this timeout, the node will become suspected.
     * Will then send K-pings by asking K of its alive nodes to ping the node who didnt respond to the ping.
     */
    private Handler<PongTimeout> handlePongTimeout = new Handler<PongTimeout>() {

        @Override
        public void handle(PongTimeout pongTimeout) {
            //If ping timed out without any pong as response...
            if (sentPingNrs.contains(pongTimeout.getPingNr())) {
                if (ENABLE_LOGGING) {
                    log.info("{} Suspected missing ping nr {} from node: {}", new Object[]{selfAddress.getId(), pongTimeout.getPingNr(), pongTimeout.getAddress()});
                }

                //Add the node to our suspected list.
                nodeHandler.addSuspected(pongTimeout.getAddress());

                //Get a random selection of our alive nodes to K-ping.
                List<NatedAddress> aliveNodes = new ArrayList<>(nodeHandler.getAliveNodes().keySet());
                aliveNodes.remove(pongTimeout.getAddress());
                Collections.shuffle(aliveNodes, rand);

                //Send K indirect pings.
                for (int i = 0; i < K && i < aliveNodes.size(); i++) {
                    if (ENABLE_LOGGING) {
                        log.info("{} sending KPing for suspected node {} to: {}", new Object[]{selfAddress.getId(), pongTimeout.getAddress(), aliveNodes.get(i)});
                    }

                    trigger(new NetKPing(selfAddress, aliveNodes.get(i), pongTimeout.getAddress(), pongTimeout.getPingNr()), network);
                }

                //Start another timer for the K-pings to finnish before we declare the node suspected.
                ScheduleTimeout scheduleTimeout = new ScheduleTimeout(SUSPECTED_TIMEOUT);
                SuspectedTimeout suspectedTimeout = new SuspectedTimeout(scheduleTimeout, pongTimeout.getAddress(), pongTimeout.getPingNr());
                scheduleTimeout.setTimeoutEvent(suspectedTimeout);
                trigger(scheduleTimeout, timer);
            }
        }
    };

    /**
     * Handler for receiving suspected timeout.
     * Is triggering a certain time after the ping timed out and we sent the K-pings.
     * If still no pong is received the node will be declared suspected.
     */
    private Handler<SuspectedTimeout> handleSuspectedTimeout = new Handler<SuspectedTimeout>() {

        @Override
        public void handle(SuspectedTimeout suspectedTimeout) {
            //If k-pings also timed out and the node is still suspected, declare the node dead.
            if (sentPingNrs.contains(suspectedTimeout.getPingNr())) {
                if (ENABLE_LOGGING) {
                    log.info("{} Suspected node: {}", new Object[]{selfAddress.getId(), suspectedTimeout.getAddress()});
                }

                //Start another timer for the K-pings to finnish before we declare the node suspected.
                ScheduleTimeout scheduleTimeout = new ScheduleTimeout(DEAD_TIMEOUT);
                DeadTimeout deadTimeout = new DeadTimeout(scheduleTimeout, suspectedTimeout.getAddress(), suspectedTimeout.getPingNr());
                scheduleTimeout.setTimeoutEvent(deadTimeout);
                trigger(scheduleTimeout, timer);
            }
        }
    };

    /**
     * Handler for receiving dead timeout.
     * Is triggering a certain time after the K-pings have timed out.
     * If still no pong is received and the node is still suspected, the node will be declared dead.
     */
    private Handler<DeadTimeout> handleDeadTimeout = new Handler<DeadTimeout>() {

        @Override
        public void handle(DeadTimeout deadTimeout) {
            //If k-pings also timed out and the node is still suspected, declare the node dead.
            if (sentPingNrs.contains(deadTimeout.getPingNr()) && nodeHandler.addDead(deadTimeout.getAddress())) {
                if (ENABLE_LOGGING) {
                    log.info("{} Declared node dead: {}", new Object[]{selfAddress.getId(), deadTimeout.getAddress()});
                }
            }
        }
    };
    
    private Handler<AddTimeout> handleAddTimeout = new Handler<AddTimeout>() {

        @Override
        public void handle(AddTimeout addTimeout) {
            trigger(new NetAddOriginalFile(selfAddress, selfAddress, ORIGINAL_FILE), network);
        }
    };

    private void schedulePeriodicPing() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, 1000);
        PingTimeout sc = new PingTimeout(spt);
        spt.setTimeoutEvent(sc);
        pingTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelPeriodicPing() {
        CancelTimeout cpt = new CancelTimeout(pingTimeoutId);
        trigger(cpt, timer);
        pingTimeoutId = null;
    }

    private void schedulePeriodicStatus() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, AGGREGATOR_TIMEOUT);
        StatusTimeout sc = new StatusTimeout(spt);
        spt.setTimeoutEvent(sc);
        statusTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelPeriodicStatus() {
        CancelTimeout cpt = new CancelTimeout(statusTimeoutId);
        trigger(cpt, timer);
        statusTimeoutId = null;
    }
    
    private void scheduleAddOrigFile() {
        ScheduleTimeout st = new ScheduleTimeout(700);
        AddTimeout at = new AddTimeout(st);
        st.setTimeoutEvent(at);
        trigger(st, timer);
    }

    //---FILE STORAGE STUFF---
    private Handler<NetAddFile> handleAddFile = new Handler<NetAddFile>() {

        @Override
        public void handle(NetAddFile file) {
        	// Append the blocks to the existing collection
            if(storedData.containsKey(file.getContent().getFilename())){
                Set<DataBlock> storedFiles = storedData.get(file.getContent().getFilename());
                storedFiles.addAll(file.getContent().getDataBlocks());
            }
            // Create new collection to house the data blocks
            else{
                Set<DataBlock> newSet = new HashSet<>();
                newSet.addAll(file.getContent().getDataBlocks());
                storedData.put(file.getContent().getFilename(), newSet);
            }
            System.out.println("Added new file '"+file.getContent().getFilename()+"' to map.");
            log.info("Added new file '"+file.getContent().getFilename()+"' to map.");
        }
    };
    
    private Handler<NetRetrieveFile> handleRetrieveFile = new Handler<NetRetrieveFile>() {
    	
        @Override
        public void handle(NetRetrieveFile retrieveFile) {
        	if (storedData.containsKey(retrieveFile.getContent().getFileName())) {
	        	// Fetch all relevant datablocks
	        	Set<DataBlock> blocks = storedData.get(retrieveFile.getContent().getFileName());
	        	
	        	// Send back all relevant blocks
	    		NetSendDataBlocks msg = new NetSendDataBlocks(selfAddress, retrieveFile.getSource(), blocks);
	    		trigger(msg, network);
        	} else {
        		// Send empty message
        		NetSendDataBlocks msg = new NetSendDataBlocks(selfAddress, retrieveFile.getSource(), new HashSet<>());
	    		trigger(msg, network);
        	}
        }
        
    };
    
    private Handler<NetRemoveFile> handleRemoveFile = new Handler<NetRemoveFile>() {
        @Override
        public void handle(NetRemoveFile RemoveFile) {
        	// Locate and delete local storage of file.
        	storedData.remove(RemoveFile.getContent().getFileName());
        }
    };
    
    private Handler<NetAddOriginalFile> handleAddOriginalFile = new Handler<NetAddOriginalFile>() {

		@Override
		public void handle(NetAddOriginalFile arg0) {
			// Locate file on FS
			final Path FILEPATH = Paths.get(arg0.getContent().getFileName());
			File f = FILEPATH.toFile();
			if (!f.exists() || f.isDirectory()) {
				log.info("Cannot find file. \n"+FILEPATH);
			} else {
			
				// Encode file
				Set<DataBlock> blocks = new HashSet<>();
				// ==== ENCODE CODE ====
	            FountainEncoder fountainCoder = new FountainEncoder(FILEPATH, BYTES_TO_READ); //New encoder with a Path to read
	            Semaphore s = fountainCoder.dropsletsSemaphore();   //Semaphore to see if there are new droplets available
	            ConcurrentLinkedQueue<DataBlock> result = fountainCoder.getQueue();    //Queue with the output
	            long startTime = System.currentTimeMillis();
	            fountainCoder.start();   //Start the encoder in a new thread
	            int counter = 0;        //Count the number of droplets received
	            long totalSize = 0;     //Count the total size of the output
	            
	            boolean firstAcquire = true;
	            Semaphore done = fountainCoder.getDoneLock();
	            while (!done.tryAcquire() || result.peek() != null) {   //Run as long as we're getting blocks
	                boolean acquired = false;   //See if there are new blocks available
	                try {
	                    acquired = s.tryAcquire(100, TimeUnit.MILLISECONDS);
	                } catch (InterruptedException e) {
	                    e.printStackTrace();
	                }
	
	                if (acquired) { //We've gotten a new block
	
	                    DataBlock block = result.poll();   //retrive the block
	                    totalSize = totalSize + block.getData().length;
	                    blocks.add(block);      //Add the block to the block list
	                    
	                    counter++;
	                }
	
	            }
				// ==== END ENCODE CODE ====
				log.info("Encode complete. Distributing files");
				Iterator<DataBlock> blocksIterator = blocks.iterator();
				
				// Send droplets to n nodes
				int blocksPerNode = blocks.size() / nodeHandler.getAliveNodes().size();
				
				// TODO(Douglas): Add option to only distribute data to subset of nodes
				for (NatedAddress node : nodeHandler.getAliveNodes().keySet()) {
					// Split block set into subsets of size n
					Set<DataBlock> nodeBlockSet = new HashSet<>();
					
					for (int i = 0; i < blocksPerNode; i++) {
						// Exit the loop if there are no more blocks.
						// Should only trigger on the last node in the list.
						if (!blocksIterator.hasNext()) break;
						
						nodeBlockSet.add(blocksIterator.next());
					}
					
					if (!nodeBlockSet.isEmpty()) {
						// Send the droplets to the node
						trigger(new NetAddFile(selfAddress, node, arg0.getContent().getFileName(), nodeBlockSet), network);
					}
				}
			}
			
		}
    
    };
    
    private Handler<NetRetrieveOriginalFile> handleRetrieveOriginalFile = new Handler<NetRetrieveOriginalFile>() {

		@Override
		public void handle(NetRetrieveOriginalFile arg0) {
			String filename = arg0.getContent().getFileName();
			// Clear the waiting list
			nodeWaitList.clear();
			for (NatedAddress node : nodeHandler.getAliveNodes().keySet()) {
				trigger(new NetRetrieveFile(selfAddress, node, new RetrieveFile(filename, selfAddress)), network);
				nodeWaitList.add(node);
			}
			
			// Wait for all responses
			Thread waitingThread = new Thread(new Runnable() {

				@Override
				public void run() {
					long start = System.currentTimeMillis() / 1000;
					long elapsedTime = (System.currentTimeMillis() / 1000) - start;
					while(!nodeWaitList.isEmpty() && elapsedTime < 30) {
						try {
							Thread.sleep(100);
							elapsedTime = (System.currentTimeMillis() / 1000) - start;
							log.info("Node: elapsedTime (" + elapsedTime + ")");
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				
			});
			
			waitingThread.start();
			try {
				waitingThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}			
			// All nodes have answered.

			// Add own blocks to file			
			Set<DataBlock> blocks = fileDataBlocks.get(filename);
			blocks.addAll(storedData.get(filename));
			
			// Decode file
			FountainDecoder decoder = new FountainDecoder(FountainEncoder.getParameters(FountainEncoder.MAX_DATA_LEN));  //Create a new decoder with the same parameters as the encoder
            blocks.forEach(decoder::addDataBlock);

            decoder.setParameters(FountainEncoder.getParameters(FountainEncoder.MAX_DATA_LEN));
            decoder.start();
            try {
                decoder.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
			// Return to caller
            log.info("Decode done");
			
		}
    
    };
    
    private Handler<NetRemoveOriginalFile> handleRemoveOriginalFile = new Handler<NetRemoveOriginalFile>() {

		@Override
		public void handle(NetRemoveOriginalFile arg0) {
			// Request deletion from all nodes
			for (NatedAddress node : nodeHandler.getAliveNodes().keySet()) {
				trigger(new NetRemoveFile(selfAddress, node, arg0.getContent().getFileName()), network);
			}			
		}
		
    };
    
    private Handler<NetSendDataBlocks> handleSendDataBlocks = new Handler<NetSendDataBlocks>() {
		
		@Override
		public void handle(NetSendDataBlocks sentDataBlocks) {
			// Message with requested blocks has arrived.
			// Check if the node had any relevant blocks.
			Set<DataBlock> receivedBlocks = sentDataBlocks.getContent().getDataBlocks();
			if (!receivedBlocks.isEmpty()) {
				for (DataBlock block : receivedBlocks) {
					// Add the new blocks to the file set
					fileDataBlocks.get(block.getFilename()).add(block);
				}				
			}
			
			// Remove the node from the waiting list
			nodeWaitList.remove(sentDataBlocks.getSource());			
		}
	};

}
