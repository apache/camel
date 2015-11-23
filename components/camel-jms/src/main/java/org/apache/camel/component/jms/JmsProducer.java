/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jms;

import static org.apache.camel.component.jms.JmsMessageHelper.isQueuePrefix;
import static org.apache.camel.component.jms.JmsMessageHelper.isTopicPrefix;
import static org.apache.camel.component.jms.JmsMessageHelper.normalizeDestinationName;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.component.jms.JmsConfiguration.CamelJmsTemplate;
import org.apache.camel.component.jms.reply.QueueReplyManager;
import org.apache.camel.component.jms.reply.ReplyManager;
import org.apache.camel.component.jms.reply.TemporaryQueueReplyManager;
import org.apache.camel.component.jms.reply.UseMessageIdAsCorrelationIdMessageSentCallback;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.MessageCreator;
import org.springframework.jms.support.JmsUtils;

/**
 * @version
 */
public class JmsProducer extends DefaultAsyncProducer {
    private static final Logger LOG = LoggerFactory.getLogger(JmsProducer.class);
    private static final String GENERATED_CORRELATION_ID_PREFIX = "Camel-";
    private final JmsEndpoint endpoint;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private JmsOperations inOnlyTemplate;
    private JmsOperations inOutTemplate;
    private UuidGenerator uuidGenerator;
    private ReplyManager replyManager;

    public JmsProducer(JmsEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public JmsEndpoint getEndpoint() {
        return (JmsEndpoint) super.getEndpoint();
    }

    protected void initReplyManager() {
        if (!started.get()) {
            synchronized (this) {
                if (started.get()) {
                    return;
                }

                // must use the classloader from the application context when creating reply manager,
                // as it should inherit the classloader from app context and not the current which may be
                // a different classloader
                ClassLoader current = Thread.currentThread().getContextClassLoader();
                ClassLoader ac = endpoint.getCamelContext().getApplicationContextClassLoader();
                try {
                    if (ac != null) {
                        Thread.currentThread().setContextClassLoader(ac);
                    }
                    // validate that replyToType and replyTo is configured accordingly
                    if (endpoint.getReplyToType() != null) {
                        // setting temporary with a fixed replyTo is not supported
                        if (endpoint.getReplyTo() != null && endpoint.getReplyToType().equals(ReplyToType.Temporary.name())) {
                            throw new IllegalArgumentException("ReplyToType " + ReplyToType.Temporary + " is not supported when replyTo "
                                    + endpoint.getReplyTo() + " is also configured.");
                        }
                    }

                    if (endpoint.getReplyTo() != null) {
                        replyManager = createReplyManager(endpoint.getReplyTo());
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Using JmsReplyManager: {} to process replies from: {}", replyManager, endpoint.getReplyTo());
                        }
                    } else {
                        replyManager = createReplyManager();
                        LOG.debug("Using JmsReplyManager: {} to process replies from temporary queue", replyManager);
                    }
                } catch (Exception e) {
                    throw new FailedToCreateProducerException(endpoint, e);
                } finally {
                    if (ac != null) {
                        Thread.currentThread().setContextClassLoader(current);
                    }
                }
                started.set(true);
            }
        }
    }

    protected void unInitReplyManager() {
        try {
            if (replyManager != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Stopping JmsReplyManager: {} from processing replies from: {}", replyManager,
                            endpoint.getReplyTo() != null ? endpoint.getReplyTo() : "temporary queue");
                }
                ServiceHelper.stopService(replyManager);
            }
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        } finally {
            started.set(false);
        }
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        // deny processing if we are not started
        if (!isRunAllowed()) {
            if (exchange.getException() == null) {
                exchange.setException(new RejectedExecutionException());
            }
            // we cannot process so invoke callback
            callback.done(true);
            return true;
        }

        try {
            if (!endpoint.isDisableReplyTo() && exchange.getPattern().isOutCapable()) {
                // in out requires a bit more work than in only
                return processInOut(exchange, callback);
            } else {
                // in only
                return processInOnly(exchange, callback);
            }
        } catch (Throwable e) {
            // must catch exception to ensure callback is invoked as expected
            // to let Camel error handling deal with this
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    protected boolean processInOut(final Exchange exchange, final AsyncCallback callback) {
        final org.apache.camel.Message in = exchange.getIn();

        String destinationName = in.getHeader(JmsConstants.JMS_DESTINATION_NAME, String.class);
        // remove the header so it wont be propagated
        in.removeHeader(JmsConstants.JMS_DESTINATION_NAME);
        if (destinationName == null) {
            destinationName = endpoint.getDestinationName();
        }

        Destination destination = in.getHeader(JmsConstants.JMS_DESTINATION, Destination.class);
        // remove the header so it wont be propagated
        in.removeHeader(JmsConstants.JMS_DESTINATION);
        if (destination == null) {
            destination = endpoint.getDestination();
        }
        if (destination != null) {
            // prefer to use destination over destination name
            destinationName = null;
        }

        initReplyManager();

        // the request timeout can be overruled by a header otherwise the endpoint configured value is used
        final long timeout = exchange.getIn().getHeader(JmsConstants.JMS_REQUEST_TIMEOUT, endpoint.getRequestTimeout(), long.class);

        // when using message id as correlation id, we need at first to use a provisional correlation id
        // which we then update to the real JMSMessageID when the message has been sent
        // this is done with the help of the MessageSentCallback
        final boolean msgIdAsCorrId = endpoint.getConfiguration().isUseMessageIDAsCorrelationID();
        final String provisionalCorrelationId = msgIdAsCorrId ? getUuidGenerator().generateUuid() : null;
        MessageSentCallback messageSentCallback = null;
        if (msgIdAsCorrId) {
            messageSentCallback = new UseMessageIdAsCorrelationIdMessageSentCallback(replyManager, provisionalCorrelationId, timeout);
        }

        final String originalCorrelationId = in.getHeader("JMSCorrelationID", String.class);
        boolean generateFreshCorrId = (ObjectHelper.isEmpty(originalCorrelationId) && !msgIdAsCorrId)
                || (originalCorrelationId != null && originalCorrelationId.startsWith(GENERATED_CORRELATION_ID_PREFIX));
        if (generateFreshCorrId) {
            // we append the 'Camel-' prefix to know it was generated by us
            in.setHeader("JMSCorrelationID", GENERATED_CORRELATION_ID_PREFIX + getUuidGenerator().generateUuid());
        }

        MessageCreator messageCreator = null;
        JmsConfiguration configuration = getEndpoint().getConfiguration();
        int maxMessageSize = configuration.getMaxChunkSize();
        if (maxMessageSize != 0 && configuration.isTransacted() && in.getBody() != null) {
            messageCreator = new MessageCreatorReqReplySplit(endpoint, exchange, replyManager, provisionalCorrelationId,
                    originalCorrelationId, timeout, callback, in, maxMessageSize);
        } else {
            messageCreator = new MessageCreatorReqReplyImpl(endpoint, exchange, replyManager, provisionalCorrelationId, in,
                    originalCorrelationId, timeout, callback);
        }
        doSend(true, destinationName, destination, messageCreator, messageSentCallback);

        // after sending then set the OUT message id to the JMSMessageID so its identical
        setMessageId(exchange);

        // continue routing asynchronously (reply will be processed async when its received)
        return false;
    }

    /**
     * Strategy to determine which correlation id to use among
     * <tt>JMSMessageID</tt> and <tt>JMSCorrelationID</tt>.
     *
     * @param message
     *            the JMS message
     * @param provisionalCorrelationId
     *            an optional provisional correlation id, which is preferred to
     *            be used
     * @return the correlation id to use
     * @throws JMSException
     *             can be thrown
     */
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

    protected boolean processInOnly(final Exchange exchange, final AsyncCallback callback) {
        final org.apache.camel.Message in = exchange.getIn();

        String destinationName = in.getHeader(JmsConstants.JMS_DESTINATION_NAME, String.class);
        if (destinationName != null) {
            // remove the header so it wont be propagated
            in.removeHeader(JmsConstants.JMS_DESTINATION_NAME);
        }
        if (destinationName == null) {
            destinationName = endpoint.getDestinationName();
        }

        Destination destination = in.getHeader(JmsConstants.JMS_DESTINATION, Destination.class);
        if (destination != null) {
            // remove the header so it wont be propagated
            in.removeHeader(JmsConstants.JMS_DESTINATION);
        }
        if (destination == null) {
            destination = endpoint.getDestination();
        }
        if (destination != null) {
            // prefer to use destination over destination name
            destinationName = null;
        }
        final String to = destinationName != null ? destinationName : "" + destination;
        MessageSentCallback messageSentCallback = getEndpoint().getConfiguration().isIncludeSentJMSMessageID() ? new InOnlyMessageSentCallback(
                exchange) : null;

        MessageCreator messageCreator = null;
        JmsConfiguration configuration = getEndpoint().getConfiguration();
        int maxMessageSize = configuration.getMaxChunkSize();
        if (maxMessageSize != 0 && configuration.isTransacted()) {
            messageCreator = new MessageCreatorSplitImpl(endpoint, in, exchange, to, maxMessageSize, log);
        } else {
            messageCreator = new MessageCreatorImpl(endpoint, in, exchange, to, log);
        }

        doSend(false, destinationName, destination, messageCreator, messageSentCallback);

        // after sending then set the OUT message id to the JMSMessageID so its identical
        setMessageId(exchange);

        // we are synchronous so return true
        callback.done(true);
        return true;
    }

    /**
     * Sends the message using the JmsTemplate.
     *
     * @param inOut
     *            use inOut or inOnly template
     * @param destinationName
     *            the destination name
     * @param destination
     *            the destination (if no name provided)
     * @param messageCreator
     *            the creator to create the {@link Message} to send
     * @param callback
     *            optional callback to invoke when message has been sent
     */
    protected void doSend(boolean inOut, String destinationName, Destination destination, MessageCreator messageCreator,
            MessageSentCallback callback) {

        CamelJmsTemplate template = (CamelJmsTemplate) (inOut ? getInOutTemplate() : getInOnlyTemplate());

        if (LOG.isTraceEnabled()) {
            LOG.trace("Using {} jms template", inOut ? "inOut" : "inOnly");
        }

        // destination should be preferred
        if (destination != null) {
            if (inOut) {
                if (template != null) {
                    template.send(destination, messageCreator, callback);
                }
            } else {
                if (template != null) {
                    template.send(destination, messageCreator, callback);
                }
            }
        } else if (destinationName != null) {
            if (inOut) {
                if (template != null) {
                    template.send(destinationName, messageCreator, callback);
                }
            } else {
                if (template != null) {
                    template.send(destinationName, messageCreator, callback);
                }
            }
        } else {
            throw new IllegalArgumentException("Neither destination nor destinationName is specified on this endpoint: " + endpoint);
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

    protected void setMessageId(Exchange exchange) {
        if (exchange.hasOut()) {
            JmsMessage out = exchange.getOut(JmsMessage.class);
            try {
                if (out != null && out.getJmsMessage() != null) {
                    out.setMessageId(out.getJmsMessage().getJMSMessageID());
                }
            } catch (JMSException e) {
                LOG.warn("Unable to retrieve JMSMessageID from outgoing JMS Message and set it into Camel's MessageId", e);
            }
        }
    }

    public JmsOperations getInOnlyTemplate() {
        if (inOnlyTemplate == null) {
            inOnlyTemplate = endpoint.createInOnlyTemplate();
        }
        return inOnlyTemplate;
    }

    public void setInOnlyTemplate(JmsOperations inOnlyTemplate) {
        this.inOnlyTemplate = inOnlyTemplate;
    }

    public JmsOperations getInOutTemplate() {
        if (inOutTemplate == null) {
            inOutTemplate = endpoint.createInOutTemplate();
        }
        return inOutTemplate;
    }

    public void setInOutTemplate(JmsOperations inOutTemplate) {
        this.inOutTemplate = inOutTemplate;
    }

    public UuidGenerator getUuidGenerator() {
        return uuidGenerator;
    }

    public void setUuidGenerator(UuidGenerator uuidGenerator) {
        this.uuidGenerator = uuidGenerator;
    }

    /**
     * Pre tests the connection before starting the listening.
     * <p/>
     * In case of connection failure the exception is thrown which prevents
     * Camel from starting.
     *
     * @throws FailedToCreateProducerException
     *             is thrown if testing the connection failed
     */
    protected void testConnectionOnStartup() throws FailedToCreateProducerException {
        try {
            CamelJmsTemplate template = (CamelJmsTemplate) getInOnlyTemplate();

            if (log.isDebugEnabled()) {
                log.debug("Testing JMS Connection on startup for destination: " + template.getDefaultDestinationName());
            }

            Connection conn = template.getConnectionFactory().createConnection();
            JmsUtils.closeConnection(conn);

            log.debug("Successfully tested JMS Connection on startup for destination: " + template.getDefaultDestinationName());
        } catch (Exception e) {
            throw new FailedToCreateProducerException(getEndpoint(), e);
        }
    }

    protected void doStart() throws Exception {
        super.doStart();
        if (uuidGenerator == null) {
            // use the generator configured on the camel context
            uuidGenerator = getEndpoint().getCamelContext().getUuidGenerator();
        }
        if (endpoint.isTestConnectionOnStartup()) {
            testConnectionOnStartup();
        }
    }

    protected void doStop() throws Exception {
        super.doStop();

        // must stop/un-init reply manager if it was in use
        unInitReplyManager();
    }

    protected ReplyManager createReplyManager() throws Exception {
        // use a temporary queue
        ReplyManager replyManager = new TemporaryQueueReplyManager(getEndpoint().getCamelContext());
        replyManager.setEndpoint(getEndpoint());

        String name = "JmsReplyManagerTimeoutChecker[" + getEndpoint().getEndpointConfiguredDestinationName() + "]";
        ScheduledExecutorService replyManagerExecutorService = getEndpoint().getCamelContext().getExecutorServiceManager()
                .newSingleThreadScheduledExecutor(name, name);
        replyManager.setScheduledExecutorService(replyManagerExecutorService);
        ServiceHelper.startService(replyManager);

        return replyManager;
    }

    protected ReplyManager createReplyManager(String replyTo) throws Exception {
        // use a regular queue
        ReplyManager replyManager = new QueueReplyManager(getEndpoint().getCamelContext());
        replyManager.setEndpoint(getEndpoint());

        String name = "JmsReplyManagerTimeoutChecker[" + replyTo + "]";
        ScheduledExecutorService replyManagerExecutorService = getEndpoint().getCamelContext().getExecutorServiceManager()
                .newSingleThreadScheduledExecutor(name, name);
        replyManager.setScheduledExecutorService(replyManagerExecutorService);
        ServiceHelper.startService(replyManager);

        return replyManager;
    }

}
