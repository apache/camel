package org.apache.camel.component.sjms.jms;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

/**
 * Default implementation of DestinationCreationStrategy, delegates to Session.createTopic
 * and Session.createQueue.
 *
 * @see org.apache.camel.component.sjms.jms.DestinationCreationStrategy
 * @see javax.jms.Session
 */
public class DefaultDestinationCreationStrategy implements DestinationCreationStrategy {
    private static final String TOPIC_PREFIX = "topic://";
    private static final String QUEUE_PREFIX = "queue://";

    @Override
    public Destination createDestination(final Session session, String name, final boolean topic) throws JMSException {
        Destination destination;
        if (topic) {
            if (name.startsWith(TOPIC_PREFIX)) {
                name = name.substring(TOPIC_PREFIX.length());
            }
            destination = session.createTopic(name);
        } else {
            if (name.startsWith(QUEUE_PREFIX)) {
                name = name.substring(QUEUE_PREFIX.length());
            }
            destination = session.createQueue(name);
        }
        return destination;
    }

    @Override
    public Destination createTemporaryDestination(final Session session, final boolean topic) throws JMSException {
        if (topic) {
            return session.createTemporaryTopic();
        } else {
            return session.createTemporaryQueue();
        }
    }
}