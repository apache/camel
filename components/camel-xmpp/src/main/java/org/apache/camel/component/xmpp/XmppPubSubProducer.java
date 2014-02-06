package org.apache.camel.component.xmpp;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.impl.DefaultProducer;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.pubsub.packet.PubSub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmppPubSubProducer extends DefaultProducer {
	private static final transient Logger LOG = LoggerFactory.getLogger(XmppPrivateChatProducer.class);
    private final XmppEndpoint endpoint;
    private XMPPConnection connection;

    public XmppPubSubProducer(XmppEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        LOG.debug("Creating XmppPresenceProducer");
    }

	public void process(Exchange exchange) throws Exception {
        try {
            if (connection == null) {
                connection = endpoint.createConnection();
            }

            // make sure we are connected
            if (!connection.isConnected()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Reconnecting to: " + XmppEndpoint.getConnectionMessage(connection));
                }
                connection.connect();
            }
        } catch (XMPPException e) {
            throw new RuntimeExchangeException("Cannot connect to XMPP Server: "
                    + ((connection != null) ? XmppEndpoint.getConnectionMessage(connection): endpoint.getHost()), exchange, e);
        }
        
        try {
            Object body = exchange.getIn().getBody(Object.class);
            if(body instanceof PubSub) {
            	PubSub pubsubpacket = (PubSub) body;
                endpoint.getBinding().populateXmppPacket(pubsubpacket, exchange);
            	exchange.getIn().setHeader(XmppConstants.docHeader, pubsubpacket);
            	connection.sendPacket(pubsubpacket);
            } else {
                throw new Exception("Message does not contain a pubsub packet");        	
            }        	
        } catch (XMPPException xmppe) {
            throw new RuntimeExchangeException("Cannot send XMPP pubsub: from " + endpoint.getUser()
                    + " to: " + XmppEndpoint.getConnectionMessage(connection), exchange, xmppe);
        } catch (Exception e) {
            throw new RuntimeExchangeException("Cannot send XMPP pubsub: from " + endpoint.getUser()
                    + " to: " + XmppEndpoint.getConnectionMessage(connection), exchange, e);
        }
	}

}
