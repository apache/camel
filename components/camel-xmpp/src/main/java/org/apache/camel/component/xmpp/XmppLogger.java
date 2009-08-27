package org.apache.camel.component.xmpp;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.Packet;

public class XmppLogger implements PacketListener {

    private static final transient Log LOG = LogFactory.getLog(XmppLogger.class);
    private String direction;

    public XmppLogger(String direction) {
        this.direction = direction;
    }

    public void processPacket(Packet packet) {
        if ( LOG.isDebugEnabled() ) {
            LOG.debug(direction + " : " + packet.toXML());
        }
    }
}
