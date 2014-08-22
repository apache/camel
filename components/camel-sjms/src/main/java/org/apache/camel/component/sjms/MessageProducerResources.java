package org.apache.camel.component.sjms;

import javax.jms.MessageProducer;
import javax.jms.Session;

/**
 * The {@link MessageProducer} resources for all {@link SjmsProducer}
 * classes.
 */
public class MessageProducerResources {

    private final Session session;
    private final MessageProducer messageProducer;
    private final TransactionCommitStrategy commitStrategy;

    public MessageProducerResources(Session session, MessageProducer messageProducer) {
        this(session, messageProducer, null);
    }

    public MessageProducerResources(Session session, MessageProducer messageProducer, TransactionCommitStrategy commitStrategy) {
        this.session = session;
        this.messageProducer = messageProducer;
        this.commitStrategy = commitStrategy;
    }

    /**
     * Gets the Session value of session for this instance of
     * MessageProducerResources.
     * 
     * @return the session
     */
    public Session getSession() {
        return session;
    }

    /**
     * Gets the QueueSender value of queueSender for this instance of
     * MessageProducerResources.
     * 
     * @return the queueSender
     */
    public MessageProducer getMessageProducer() {
        return messageProducer;
    }

    /**
     * Gets the TransactionCommitStrategy value of commitStrategy for this
     * instance of SjmsProducer.MessageProducerResources.
     * 
     * @return the commitStrategy
     */
    public TransactionCommitStrategy getCommitStrategy() {
        return commitStrategy;
    }
}