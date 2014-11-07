package org.apache.camel.component.sjms.jms;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

/**
 * Strategy for creating Destination's
 */
public interface DestinationCreationStrategy {
    Destination createDestination(Session session, String name, boolean topic) throws JMSException;
    Destination createTemporaryDestination(Session session, boolean topic) throws JMSException;
}
