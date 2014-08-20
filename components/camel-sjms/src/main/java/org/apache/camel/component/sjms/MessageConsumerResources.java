package org.apache.camel.component.sjms;

import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.Session;

public class MessageConsumerResources {

    private final Session session;
    private final MessageConsumer messageConsumer;
    private final Destination replyToDestination;

    public MessageConsumerResources(MessageConsumer messageConsumer) {
        this(null, messageConsumer, null);
    }

    public MessageConsumerResources(Session session, MessageConsumer messageConsumer) {
        this(session, messageConsumer, null);
    }

    /**
     * TODO Add Constructor Javadoc
     * 
     * @param session
     * @param messageConsumer
     */
    public MessageConsumerResources(Session session, MessageConsumer messageConsumer, Destination replyToDestination) {
        this.session = session;
        this.messageConsumer = messageConsumer;
        this.replyToDestination = replyToDestination;
    }

    /**
     * Gets the Session value of session for this instance of
     * MessageProducerModel.
     * 
     * @return the session
     */
    public Session getSession() {
        return session;
    }

    /**
     * Gets the QueueSender value of queueSender for this instance of
     * MessageProducerModel.
     * 
     * @return the queueSender
     */
    public MessageConsumer getMessageConsumer() {
        return messageConsumer;
    }

    public Destination getReplyToDestination() {
        return replyToDestination;
    }
}