/*
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

import java.util.concurrent.ExecutorService;
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
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.component.jms.JmsConfiguration.CamelJmsTemplate;
import org.apache.camel.component.jms.reply.QueueReplyManager;
import org.apache.camel.component.jms.reply.ReplyManager;
import org.apache.camel.component.jms.reply.TemporaryQueueReplyManager;
import org.apache.camel.component.jms.reply.UseMessageIdAsCorrelationIdMessageSentCallback;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.MessageCreator;
import org.springframework.jms.support.JmsUtils;

import static java.util.Optional.ofNullable;
import static org.apache.camel.component.jms.JmsMessageHelper.isQueuePrefix;
import static org.apache.camel.component.jms.JmsMessageHelper.isTopicPrefix;
import static org.apache.camel.component.jms.JmsMessageHelper.normalizeDestinationName;

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
                ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
                ClassLoader ac = endpoint.getCamelContext().getApplicationContextClassLoader();
                try {
                    if (ac != null) {
                        Thread.currentThread().setContextClassLoader(ac);
                    }
                    // validate that replyToType and replyTo is configured accordingly
                    if (endpoint.getReplyToType() != null) {
                        // setting temporary with a fixed replyTo is not supported
                        if (endpoint.getReplyTo() != null && endpoint.getReplyToType().equals(ReplyToType.Temporary.name())) {
                            throw new IllegalArgumentException("ReplyToType " + ReplyToType.Temporary
                                    + " is not supported when replyTo " + endpoint.getReplyTo() + " is also configured.");
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
                    Thread.currentThread().setContextClassLoader(oldClassLoader);
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
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        } finally {
            started.set(false);
        }
    }

    @Override
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

        final JmsConfiguration configuration = endpoint.getConfiguration();

        // when using message id as correlation id, we need at first to use a provisional correlation id
        // which we then update to the real JMSMessageID when the message has been sent
        // this is done with the help of the MessageSentCallback
        final boolean msgIdAsCorrId = configuration.isUseMessageIDAsCorrelationID();
        final String provisionalCorrelationId = msgIdAsCorrId ? getUuidGenerator().generateUuid() : null;
        MessageSentCallback messageSentCallback = null;
        if (msgIdAsCorrId) {
            messageSentCallback = new UseMessageIdAsCorrelationIdMessageSentCallback(replyManager, provisionalCorrelationId, timeout);
        }

        final String correlationProperty = configuration.getCorrelationProperty();

        final String correlationPropertyToUse = ofNullable(correlationProperty).orElse("JMSCorrelationID");

        final String originalCorrelationId = in.getHeader(correlationPropertyToUse, String.class);

        boolean generateFreshCorrId = (ObjectHelper.isEmpty(originalCorrelationId) && !msgIdAsCorrId) 
                || (originalCorrelationId != null && originalCorrelationId.startsWith(GENERATED_CORRELATION_ID_PREFIX));
        if (generateFreshCorrId) {
            // we append the 'Camel-' prefix to know it was generated by us
            in.setHeader(correlationPropertyToUse, GENERATED_CORRELATION_ID_PREFIX + getUuidGenerator().generateUuid());
        }

        MessageCreator messageCreator = new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                Message answer = endpoint.getBinding().makeJmsMessage(exchange, in, session, null);

                Destination replyTo = null;
                String replyToOverride = configuration.getReplyToOverride();
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

                if (correlationProperty != null) {
                    replyManager.setCorrelationProperty(correlationProperty);
                }

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Using {}: {}, JMSReplyTo destination: {}, with request timeout: {} ms.",
                           new Object[]{correlationPropertyToUse, correlationId, replyTo, timeout});
                }

                LOG.trace("Created javax.jms.Message: {}", answer);
                return answer;
            }
        };

        doSend(true, destinationName, destination, messageCreator, messageSentCallback);

        // continue routing asynchronously (reply will be processed async when its received)
        return false;
    }

    /**
     * Strategy to determine which correlation id to use among <tt>JMSMessageID</tt> and <tt>JMSCorrelationID</tt>.
     *
     * @param message   the JMS message
     * @param provisionalCorrelationId an optional provisional correlation id, which is preferred to be used
     * @return the correlation id to use
     * @throws JMSException can be thrown
     */
    protected String determineCorrelationId(Message message, String provisionalCorrelationId) throws JMSException {
        if (provisionalCorrelationId != null) {
            return provisionalCorrelationId;
        }

        final JmsConfiguration configuration = endpoint.getConfiguration();
        final String correlationProperty = configuration.getCorrelationProperty();

        final String messageId = message.getJMSMessageID();
        final String correlationId = message.getJMSCorrelationID();
        final String correlationPropertyValue;
        if (correlationProperty == null) {
            correlationPropertyValue = null;
        } else {
            correlationPropertyValue = message.getStringProperty(correlationProperty);
        }

        if (!ObjectHelper.isEmpty(correlationPropertyValue)) {
            return correlationPropertyValue;
        } else if (configuration.isUseMessageIDAsCorrelationID()) {
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
        MessageSentCallback messageSentCallback = getEndpoint().getConfiguration().isIncludeSentJMSMessageID()
                ? new InOnlyMessageSentCallback(exchange) : null;

        MessageCreator messageCreator = new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                Message answer = endpoint.getBinding().makeJmsMessage(exchange, in, session, null);

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
                        LOG.debug("Disabling JMSReplyTo: {} for destination: {}. Use preserveMessageQos=true to force Camel to keep the JMSReplyTo on endpoint: {}",
                                new Object[]{jmsReplyTo, to, endpoint});
                    }
                    jmsReplyTo = null;
                }

                // the reply to is a String, so we need to look up its Destination instance
                // and if needed create the destination using the session if needed to
                if (jmsReplyTo instanceof String) {
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
                    replyTo = (Destination)jmsReplyTo;
                }
                if (replyTo != null) {
                    LOG.debug("Using JMSReplyTo destination: {}", replyTo);
                    JmsMessageHelper.setJMSReplyTo(answer, replyTo);
                } else {
                    // do not use JMSReplyTo
                    LOG.trace("Not using JMSReplyTo");
                    JmsMessageHelper.setJMSReplyTo(answer, null);
                }

                LOG.trace("Created javax.jms.Message: {}", answer);
                return answer;
            }
        };

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
     * @param inOut           use inOut or inOnly template
     * @param destinationName the destination name
     * @param destination     the destination (if no name provided)
     * @param messageCreator  the creator to create the {@link Message} to send
     * @param callback        optional callback to invoke when message has been sent
     */
    protected void doSend(boolean inOut, String destinationName, Destination destination,
                          MessageCreator messageCreator, MessageSentCallback callback) {

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

    protected Destination resolveOrCreateDestination(String destinationName, Session session)
        throws JMSException {
        Destination dest = null;

        boolean isPubSub = isTopicPrefix(destinationName)
                           || (!isQueuePrefix(destinationName) && endpoint.isPubSubDomain());
        // try using destination resolver to lookup the destination
        if (endpoint.getDestinationResolver() != null) {
            dest = endpoint.getDestinationResolver().resolveDestinationName(session, destinationName,
                                                                            isPubSub);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Resolved JMSReplyTo destination {} using DestinationResolver {} as PubSubDomain {} -> {}",
                          new Object[] {destinationName, endpoint.getDestinationResolver(), isPubSub, dest});
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
     * In case of connection failure the exception is thrown which prevents Camel from starting.
     *
     * @throws FailedToCreateProducerException is thrown if testing the connection failed
     */
    protected void testConnectionOnStartup() throws FailedToCreateProducerException {
        try {
            CamelJmsTemplate template = (CamelJmsTemplate) getInOnlyTemplate();

            if (LOG.isDebugEnabled()) {
                LOG.debug("Testing JMS Connection on startup for destination: {}", template.getDefaultDestinationName());
            }

            Connection conn = template.getConnectionFactory().createConnection();
            JmsUtils.closeConnection(conn);

            LOG.debug("Successfully tested JMS Connection on startup for destination: {}", template.getDefaultDestinationName());
        } catch (Exception e) {
            throw new FailedToCreateProducerException(getEndpoint(), e);
        }
    }

    @Override
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

    @Override
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
        ScheduledExecutorService replyManagerScheduledExecutorService = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(name, name);
        replyManager.setScheduledExecutorService(replyManagerScheduledExecutorService);

        name = "JmsReplyManagerOnTimeout[" + getEndpoint().getEndpointConfiguredDestinationName() + "]";
        // allow the timeout thread to timeout so during normal operation we do not have a idle thread
        int max = getEndpoint().getReplyToOnTimeoutMaxConcurrentConsumers();
        if (max <= 0) {
            throw new IllegalArgumentException("The option replyToOnTimeoutMaxConcurrentConsumers must be >= 1");
        }
        ExecutorService replyManagerExecutorService = getEndpoint().getCamelContext().getExecutorServiceManager().newThreadPool(replyManager, name, 0, max);
        replyManager.setOnTimeoutExecutorService(replyManagerExecutorService);

        ServiceHelper.startService(replyManager);

        return replyManager;
    }

    protected ReplyManager createReplyManager(String replyTo) throws Exception {
        // use a regular queue
        ReplyManager replyManager = new QueueReplyManager(getEndpoint().getCamelContext());
        replyManager.setEndpoint(getEndpoint());

        String name = "JmsReplyManagerTimeoutChecker[" + replyTo + "]";
        ScheduledExecutorService replyManagerScheduledExecutorService = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(name, name);
        replyManager.setScheduledExecutorService(replyManagerScheduledExecutorService);

        name = "JmsReplyManagerOnTimeout[" + replyTo + "]";
        // allow the timeout thread to timeout so during normal operation we do not have a idle thread
        int max = getEndpoint().getReplyToOnTimeoutMaxConcurrentConsumers();
        if (max <= 0) {
            throw new IllegalArgumentException("The option replyToOnTimeoutMaxConcurrentConsumers must be >= 1");
        }
        ExecutorService replyManagerExecutorService = getEndpoint().getCamelContext().getExecutorServiceManager().newThreadPool(replyManager, name, 0, max);
        replyManager.setOnTimeoutExecutorService(replyManagerExecutorService);

        ServiceHelper.startService(replyManager);

        return replyManager;
    }

}
