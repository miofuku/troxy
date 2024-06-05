/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.communication;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import bftsmart.communication.client.CommunicationSystemServerSide;
import bftsmart.communication.client.CommunicationSystemServerSideFactory;
import bftsmart.communication.client.RequestReceiver;
import bftsmart.communication.server.ServersCommunicationLayer;
import bftsmart.consensus.roles.Acceptor;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.core.TOMLayer;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.Logger;

/**
 *
 * @author alysson
 */
public class ServerCommunicationSystem extends Thread {

    public final long MESSAGE_WAIT_TIME = 100;
    private LinkedBlockingQueue<SystemMessage> inQueue = null;//new LinkedBlockingQueue<SystemMessage>(IN_QUEUE_SIZE);
    protected MessageHandler messageHandler = new MessageHandler();
    private final ServersCommunicationLayer[] serversConn;
    private final CommunicationSystemServerSide[] clientsConn;
    private ServerViewController[] controller;

    private final Map<Integer, CommunicationSystemServerSide> clientdisp = new HashMap<>();
    private final Map<Integer, List<TOMMessage>> clientbacklog = new HashMap<>();

    /**
     * Creates a new instance of ServerCommunicationSystem
     */
    public ServerCommunicationSystem(ServerViewController[] controller, ServiceReplica replica) throws Exception {
        super("Server CS");

        this.controller = controller;

        inQueue = new LinkedBlockingQueue<SystemMessage>(controller[ 0 ].getStaticConf().getInQueueSize());

        //create a new conf, with updated port number for servers
        //TOMConfiguration serversConf = new TOMConfiguration(conf.getProcessId(),
        //      Configuration.getHomeDir(), "hosts.config");

        //serversConf.increasePortNumber();
        serversConn = new ServersCommunicationLayer[ controller.length ];
        clientsConn = new CommunicationSystemServerSide[ controller.length ];

        for( int i=0; i<controller.length; i++ )
        {
            serversConn[ i ] = new ServersCommunicationLayer(controller[ i ], inQueue, replica);
            clientsConn[ i ] = CommunicationSystemServerSideFactory.getCommunicationSystemServerSide(controller[ i ], this);
        }

    }

    public void registerClient(int clientid, CommunicationSystemServerSide con)
    {
    	List<TOMMessage> backlog = null;

    	synchronized( clientdisp )
    	{
    		clientdisp.put( clientid, con );
    		backlog = clientbacklog.remove( clientid );
    	}

    	if( backlog!=null )
    	{
    		for( TOMMessage msg : backlog )
    			con.send( new int[] { clientid }, msg, false );
    	}
    }

    //******* EDUARDO BEGIN **************//
    public void joinViewReceived() {
        for( ServersCommunicationLayer sc : serversConn )
            sc.joinViewReceived();
    }

    public void updateServersConnections() {
        for( ServersCommunicationLayer sc : serversConn )
            sc.updateConnections();
//        if (clientsConn == null) {
//            clientsConn = CommunicationSystemServerSideFactory.getCommunicationSystemServerSide(controller[ 0 ]);
//        }
    }

    //******* EDUARDO END **************//
    public void setAcceptor(Acceptor acceptor) {
        messageHandler.setAcceptor(acceptor);
    }

    public void setTOMLayer(TOMLayer tomLayer) {
        messageHandler.setTOMLayer(tomLayer);
    }

    public void setRequestReceiver(RequestReceiver requestReceiver) {
//        if (clientsConn == null) {
//            clientsConn = CommunicationSystemServerSideFactory.getCommunicationSystemServerSide(controller[ 0 ]);
//        }
        for( CommunicationSystemServerSide cc : clientsConn )
            cc.setRequestReceiver(requestReceiver);
    }

    /**
     * Thread method responsible for receiving messages sent by other servers.
     */
    @Override
    public void run() {

        long count = 0;
        while (true) {
            try {
                if (count % 1000 == 0 && count > 0) {
                    Logger.println("(ServerCommunicationSystem.run) After " + count + " messages, inQueue size=" + inQueue.size());
                }

                SystemMessage sm = inQueue.poll(MESSAGE_WAIT_TIME, TimeUnit.MILLISECONDS);

                if (sm != null) {
                    Logger.println("<-------receiving---------- " + sm);
                    messageHandler.processData(sm);
                    count++;
                } else {                
                    messageHandler.verifyPending();               
                }
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
        }
    }

    /**
     * Send a message to target processes. If the message is an instance of
     * TOMMessage, it is sent to the clients, otherwise it is set to the
     * servers.
     *
     * @param targets the target receivers of the message
     * @param sm the message to be sent
     */

    private final AtomicLong serversendcnt = new AtomicLong();

    public void send(int[] targets, SystemMessage sm) {
        if (sm instanceof TOMMessage) {
            sendToClient(targets, (TOMMessage) sm);
        } else {
            long cnt = serversendcnt.incrementAndGet();
            Logger.println("--------sending----------> " + sm);
            serversConn[ (int) ( cnt % serversConn.length ) ].send(targets, sm, true);
        }
    }

    public ServersCommunicationLayer getServersConn() {
        return serversConn[ 0 ];
    }

    public void sendToClient(int[] targets, TOMMessage sm) {
        if( targets.length!=1 )
            throw new IllegalStateException();

        CommunicationSystemServerSide con = null;

    	synchronized( clientdisp )
    	{
    		con = clientdisp.get( targets[ 0 ] );

    		if( con==null )
    		{
    			List<TOMMessage> backlog = clientbacklog.get( targets[ 0 ] );

    			if( backlog==null )
    			{
    				backlog = new LinkedList<>();
    				clientbacklog.put( targets[ 0 ], backlog );
    			}

    			backlog.add( sm );
    		}
    	}

		if( con!=null )
			con.send(targets, sm, false);
    }

    @Override
    public String toString() {
        return serversConn.toString();
    }
}
