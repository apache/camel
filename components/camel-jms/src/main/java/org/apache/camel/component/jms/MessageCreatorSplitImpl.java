package org.apache.camel.component.jms;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageCreatorSplitImpl extends MessageCreatorSplitParentImpl {

    public MessageCreatorSplitImpl(JmsEndpoint endpoint, org.apache.camel.Message in, Exchange exchange, String to, int maxSize, Logger log) {
        super(endpoint, in, exchange, LoggerFactory.getLogger(MessageCreatorSplitImpl.class), maxSize);
        this.messageCreator = new MessageCreatorImpl(endpoint, in, exchange, to, log);
    }

    @Override
    public Message createMessage(Session session) throws JMSException {
        Message answer = createMessageFromEndpoint(session);
        ((MessageCreatorImpl) messageCreator).processReply(session, answer);
        return answer;
    }
}
