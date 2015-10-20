package org.apache.camel.component.jms;

import static org.apache.camel.component.jms.JmsMessageHelper.isQueuePrefix;
import static org.apache.camel.component.jms.JmsMessageHelper.isTopicPrefix;
import static org.apache.camel.component.jms.JmsMessageHelper.normalizeDestinationName;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.MessageCreator;

public class MessageCreatorImpl implements MessageCreator {

    private static final Logger LOG = LoggerFactory.getLogger(MessageCreatorImpl.class);
    protected JmsEndpoint endpoint;
    protected org.apache.camel.Message camelMessage;
    protected Exchange exchange;
    protected String to;
    protected Logger log;

    public MessageCreatorImpl(JmsEndpoint endpoint, org.apache.camel.Message camelMessage, Exchange exchange, String to, Logger log) {
        this.endpoint = endpoint;
        this.camelMessage = camelMessage;
        this.exchange = exchange;
        this.to = to;
        this.log = log;

    }

    @Override
    public Message createMessage(Session session) throws JMSException {

        Message answer = createMessageFromEndpoint(session);

        processReply(session, answer);
        return answer;
    }

    void processReply(Session session, Message answer) throws JMSException {
        // when in InOnly mode the JMSReplyTo is a bit complicated
        // we only want to set the JMSReplyTo on the answer if
        // there is a JMSReplyTo from the header/endpoint and
        // we have been told to preserveMessageQos

        Object jmsReplyTo = JmsMessageHelper.getJMSReplyTo(answer);
        if (endpoint.isDisableReplyTo()) {
            // honor disable reply to configuration
            LOG.trace("ReplyTo is disabled on endpoint: {}", endpoint);
            JmsMessageHelper.setJMSReplyTo(answer, null);
        } else {
            // if the binding did not create the reply to then we have to try to create it here
            if (jmsReplyTo == null) {
                // prefer reply to from header over endpoint configured
                jmsReplyTo = exchange.getIn().getHeader("JMSReplyTo", String.class);
                if (jmsReplyTo == null) {
                    jmsReplyTo = endpoint.getReplyTo();
                }
            }
        }

        // we must honor these special flags to preserve QoS
        // as we are not OUT capable and thus do not expect a reply, and therefore
        // the consumer of this message should not return a reply so we remove it
        // unless we use preserveMessageQos=true to tell that we still want to use JMSReplyTo
        if (jmsReplyTo != null && !(endpoint.isPreserveMessageQos() || endpoint.isExplicitQosEnabled())) {
            // log at debug what we are doing, as higher level may cause noise in production logs
            // this behavior is also documented at the camel website
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "Disabling JMSReplyTo: {} for destination: {}. Use preserveMessageQos=true to force Camel to keep the JMSReplyTo on endpoint: {}",
                        new Object[] { jmsReplyTo, to, endpoint });
            }
            jmsReplyTo = null;
        }

        // the reply to is a String, so we need to look up its Destination instance
        // and if needed create the destination using the session if needed to
        if (jmsReplyTo != null && jmsReplyTo instanceof String) {
            String replyTo = (String) jmsReplyTo;
            // we need to null it as we use the String to resolve it as a Destination instance
            jmsReplyTo = resolveOrCreateDestination(replyTo, session);
        }

        // set the JMSReplyTo on the answer if we are to use it
        Destination replyTo = null;
        String replyToOverride = endpoint.getConfiguration().getReplyToOverride();
        if (replyToOverride != null) {
            replyTo = resolveOrCreateDestination(replyToOverride, session);
        } else if (jmsReplyTo instanceof Destination) {
            replyTo = (Destination) jmsReplyTo;
        }
        if (replyTo != null) {
            LOG.debug("Using JMSReplyTo destination: {}", replyTo);
            JmsMessageHelper.setJMSReplyTo(answer, replyTo);
        } else {
            // do not use JMSReplyTo
            log.trace("Not using JMSReplyTo");
            JmsMessageHelper.setJMSReplyTo(answer, null);
        }

        LOG.trace("Created javax.jms.Message: {}", answer);
    }

    protected Message createMessageFromEndpoint(Session session) throws JMSException {
        Message answer = endpoint.getBinding().makeJmsMessage(exchange, camelMessage, session, null);
        return answer;
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
