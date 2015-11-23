package org.apache.camel.component.jms;

import static org.apache.camel.component.jms.JmsMessageHelper.isQueuePrefix;
import static org.apache.camel.component.jms.JmsMessageHelper.isTopicPrefix;
import static org.apache.camel.component.jms.JmsMessageHelper.normalizeDestinationName;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.component.jms.reply.ReplyManager;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.MessageCreator;

public class MessageCreatorReqReplyImpl implements MessageCreator {
    private static final Logger LOG = LoggerFactory.getLogger(MessageCreatorReqReplyImpl.class);
    protected JmsEndpoint endpoint;
    protected Exchange exchange;
    protected ReplyManager replyManager;
    protected String provisionalCorrelationId;
    protected org.apache.camel.Message in;
    protected String originalCorrelationId;
    protected long timeout;
    protected AsyncCallback callback;

    public MessageCreatorReqReplyImpl(JmsEndpoint endpoint, Exchange exchange, ReplyManager replyManager, String provisionalCorrelationId,
            org.apache.camel.Message in, String originalCorrelationId, long timeout, AsyncCallback callback) {
        this.endpoint = endpoint;
        this.exchange = exchange;
        this.replyManager = replyManager;
        this.provisionalCorrelationId = provisionalCorrelationId;
        this.in = in;
        this.originalCorrelationId = originalCorrelationId;
        this.timeout = timeout;
        this.callback = callback;

    }

    @Override
    public Message createMessage(Session session) throws JMSException {
        Message answer = createMessageFromEndpoint(session);

        processReply(answer, session);
        return answer;
    }

    void processReply(Message answer, Session session) throws JMSException {
        Destination replyTo = null;
        String replyToOverride = endpoint.getConfiguration().getReplyToOverride();
        if (replyToOverride != null) {
            replyTo = resolveOrCreateDestination(replyToOverride, session);
        } else {
            // get the reply to destination to be used from the reply manager
            replyTo = replyManager.getReplyTo();
        }
        if (replyTo == null) {
            throw new RuntimeExchangeException("Failed to resolve replyTo destination", exchange);
        }
        JmsMessageHelper.setJMSReplyTo(answer, replyTo);
        replyManager.setReplyToSelectorHeader(in, answer);

        String correlationId = determineCorrelationId(answer, provisionalCorrelationId);
        replyManager.registerReply(replyManager, exchange, callback, originalCorrelationId, correlationId, timeout);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Using JMSCorrelationID: {}, JMSReplyTo destination: {}, with request timeout: {} ms.", new Object[] { correlationId,
                    replyTo, timeout });
        }

        LOG.trace("Created javax.jms.Message: {}", answer);
    }

    protected Message createMessageFromEndpoint(Session session) throws JMSException {
        Message answer = endpoint.getBinding().makeJmsMessage(exchange, in, session, null);
        return answer;
    }

    protected String determineCorrelationId(Message message, String provisionalCorrelationId) throws JMSException {
        if (provisionalCorrelationId != null) {
            return provisionalCorrelationId;
        }

        final String messageId = message.getJMSMessageID();
        final String correlationId = message.getJMSCorrelationID();
        if (endpoint.getConfiguration().isUseMessageIDAsCorrelationID()) {
            return messageId;
        } else if (ObjectHelper.isEmpty(correlationId)) {
            // correlation id is empty so fallback to message id
            return messageId;
        } else {
            return correlationId;
        }
    }

    protected Destination resolveOrCreateDestination(String destinationName, Session session) throws JMSException {
        Destination dest = null;

        boolean isPubSub = isTopicPrefix(destinationName) || (!isQueuePrefix(destinationName) && endpoint.isPubSubDomain());
        // try using destination resolver to lookup the destination
        if (endpoint.getDestinationResolver() != null) {
            dest = endpoint.getDestinationResolver().resolveDestinationName(session, destinationName, isPubSub);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Resolved JMSReplyTo destination {} using DestinationResolver {} as PubSubDomain {} -> {}", new Object[] {
                        destinationName, endpoint.getDestinationResolver(), isPubSub, dest });
            }
        }
        if (dest == null) {
            // must normalize the destination name
            String before = destinationName;
            destinationName = normalizeDestinationName(destinationName);
            LOG.trace("Normalized JMSReplyTo destination name {} -> {}", before, destinationName);

            // okay then fallback and create the queue/topic
            if (isPubSub) {
                LOG.debug("Creating JMSReplyTo topic: {}", destinationName);
                dest = session.createTopic(destinationName);
            } else {
                LOG.debug("Creating JMSReplyTo queue: {}", destinationName);
                dest = session.createQueue(destinationName);
            }
        }
        return dest;
    }
}
