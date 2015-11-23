package org.apache.camel.component.jms;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.jms.reply.ReplyManager;
import org.slf4j.LoggerFactory;

public class MessageCreatorReqReplySplit extends MessageCreatorSplitParentImpl {
    public MessageCreatorReqReplySplit(JmsEndpoint endpoint, Exchange exchange, ReplyManager replyManager, String provisionalCorrelationId,
            String originalCorrelationId, long timeout, AsyncCallback callback, org.apache.camel.Message in, int maxSize) {
        super(endpoint, in, exchange, LoggerFactory.getLogger(MessageCreatorReqReplySplit.class), maxSize);
        this.messageCreator = new MessageCreatorReqReplyImpl(endpoint, exchange, replyManager, provisionalCorrelationId, in,
                originalCorrelationId, timeout, callback);
    }

    @Override
    public Message createMessage(Session session) throws JMSException {
        Message answer = createMessageFromEndpoint(session);
        ((MessageCreatorReqReplyImpl) messageCreator).processReply(answer, session);
        return answer;
    }
}
