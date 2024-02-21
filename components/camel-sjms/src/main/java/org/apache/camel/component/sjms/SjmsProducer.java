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
package org.apache.camel.component.sjms;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.component.sjms.jms.JmsMessageHelper;
import org.apache.camel.component.sjms.jms.MessageCreator;
import org.apache.camel.component.sjms.reply.QueueReplyManager;
import org.apache.camel.component.sjms.reply.ReplyManager;
import org.apache.camel.component.sjms.reply.TemporaryQueueReplyManager;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.sjms.jms.JmsMessageHelper.*;

public class SjmsProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SjmsProducer.class);

    private static final String GENERATED_CORRELATION_ID_PREFIX = "Camel-";
    private final SjmsEndpoint endpoint;
    private final AtomicBoolean started = new AtomicBoolean();
    private SjmsTemplate inOnlyTemplate;
    private SjmsTemplate inOutTemplate;
    private UuidGenerator uuidGenerator;
    private ReplyManager replyManager;

    public SjmsProducer(SjmsEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public SjmsEndpoint getEndpoint() {
        return (SjmsEndpoint) super.getEndpoint();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        if (uuidGenerator == null) {
            // use the generator configured on the camel context
            uuidGenerator = getEndpoint().getCamelContext().getUuidGenerator();
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
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
                        if (endpoint.getReplyTo() != null && endpoint.getReplyToType().equals(ReplyToType.Temporary)) {
                            throw new IllegalArgumentException(
                                    "ReplyToType " + ReplyToType.Temporary
                                                               + " is not supported when replyTo " + endpoint.getReplyTo()
                                                               + " is also configured.");
                        }
                    }

                    if (endpoint.getReplyTo() != null) {
                        replyManager = createReplyManager(endpoint.getReplyTo());
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Using JmsReplyManager: {} to process replies from: {}", replyManager,
                                    endpoint.getReplyTo());
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

    protected ReplyManager createReplyManager(String replyTo) throws Exception {
        // use a regular queue
        ReplyManager replyManager = new QueueReplyManager(getEndpoint().getCamelContext());
        replyManager.setEndpoint(getEndpoint());

        String name = "JmsReplyManagerTimeoutChecker[" + replyTo + "]";
        ScheduledExecutorService replyManagerScheduledExecutorService
                = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(name, name);
        replyManager.setScheduledExecutorService(replyManagerScheduledExecutorService);

        name = "JmsReplyManagerOnTimeout[" + replyTo + "]";
        // allow the timeout thread to timeout so during normal operation we do not have a idle thread
        ExecutorService replyManagerExecutorService = createReplyManagerExecutor(replyManager, name);
        replyManager.setOnTimeoutExecutorService(replyManagerExecutorService);

        ServiceHelper.startService(replyManager);

        return replyManager;
    }

    protected ReplyManager createReplyManager() throws Exception {
        // use a temporary queue
        ReplyManager temporaryQueueReplyManager = new TemporaryQueueReplyManager(getEndpoint().getCamelContext());
        temporaryQueueReplyManager.setEndpoint(getEndpoint());

        String name = "JmsReplyManagerTimeoutChecker[" + getEndpoint().getEndpointConfiguredDestinationName() + "]";
        ScheduledExecutorService replyManagerScheduledExecutorService
                = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(name, name);
        temporaryQueueReplyManager.setScheduledExecutorService(replyManagerScheduledExecutorService);

        name = "JmsReplyManagerOnTimeout[" + getEndpoint().getEndpointConfiguredDestinationName() + "]";
        // allow the timeout thread to timeout so during normal operation we do not have a idle thread
        ExecutorService replyManagerExecutorService = createReplyManagerExecutor(temporaryQueueReplyManager, name);
        temporaryQueueReplyManager.setOnTimeoutExecutorService(replyManagerExecutorService);

        ServiceHelper.startService(temporaryQueueReplyManager);

        return temporaryQueueReplyManager;
    }

    private ExecutorService createReplyManagerExecutor(ReplyManager temporaryQueueReplyManager, String name) {
        int max = doGetMax();
        return getEndpoint().getCamelContext().getExecutorServiceManager().newThreadPool(temporaryQueueReplyManager, name, 0,
                max);
    }

    private int doGetMax() {
        int max = getEndpoint().getComponent().getReplyToOnTimeoutMaxConcurrentConsumers();
        if (max <= 0) {
            throw new IllegalArgumentException("The option replyToOnTimeoutMaxConcurrentConsumers must be >= 1");
        }
        return max;
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
            SjmsTemplate template = getInOnlyTemplate();

            if (LOG.isDebugEnabled()) {
                LOG.debug("Testing JMS Connection on startup for destination: {}", getEndpoint().getDestinationName());
            }

            Connection conn = template.getConnectionFactory().createConnection();
            SjmsHelper.closeConnection(conn);

            LOG.debug("Successfully tested JMS Connection on startup for destination: {}",
                    getEndpoint().getDestinationName());
        } catch (Exception e) {
            throw new FailedToCreateProducerException(getEndpoint(), e);
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
        } catch (Exception e) {
            // must catch exception to ensure callback is invoked as expected
            // to let Camel error handling deal with this
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    protected boolean processInOut(final Exchange exchange, final AsyncCallback callback) {
        final org.apache.camel.Message in = exchange.getIn();

        String destinationName = in.getHeader(SjmsConstants.JMS_DESTINATION_NAME, String.class);
        // remove the header so it won't be propagated
        in.removeHeader(SjmsConstants.JMS_DESTINATION_NAME);
        if (destinationName == null) {
            destinationName = endpoint.getDestinationName();
        }

        initReplyManager();

        // the request timeout can be overruled by a header otherwise the endpoint configured value is used
        final long timeout
                = exchange.getIn().getHeader(SjmsConstants.JMS_REQUEST_TIMEOUT, endpoint.getRequestTimeout(), long.class);

        final String originalCorrelationId = in.getHeader(SjmsConstants.JMS_CORRELATION_ID, String.class);

        boolean generateFreshCorrId = ObjectHelper.isEmpty(originalCorrelationId)
                || originalCorrelationId.startsWith(GENERATED_CORRELATION_ID_PREFIX);
        if (generateFreshCorrId) {
            // we append the 'Camel-' prefix to know it was generated by us
            in.setHeader(SjmsConstants.JMS_CORRELATION_ID, GENERATED_CORRELATION_ID_PREFIX + getUuidGenerator().generateUuid());
        }

        MessageCreator messageCreator = new MessageCreator() {
            public Message createMessage(Session session) throws JMSException {
                Message answer = endpoint.getBinding().makeJmsMessage(exchange, in, session, null);

                Destination replyTo;
                String replyToOverride = endpoint.getReplyToOverride();
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

                String correlationId = determineCorrelationId(answer);
                replyManager.registerReply(replyManager, exchange, callback, originalCorrelationId, correlationId, timeout);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Using {}: {}, JMSReplyTo destination: {}, with request timeout: {} ms.",
                            SjmsConstants.JMS_CORRELATION_ID, correlationId, replyTo, timeout);
                }

                LOG.trace("Created jakarta.jms.Message: {}", answer);
                return answer;
            }
        };

        try {
            doSend(exchange, true, destinationName, messageCreator);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // continue routing asynchronously (reply will be processed async when its received)
        return false;
    }

    protected boolean processInOnly(final Exchange exchange, final AsyncCallback callback) {
        final org.apache.camel.Message in = exchange.getIn();

        String destinationName = in.getHeader(SjmsConstants.JMS_DESTINATION_NAME, String.class);
        if (destinationName != null) {
            // remove the header so it wont be propagated
            in.removeHeader(SjmsConstants.JMS_DESTINATION_NAME);
        }
        if (destinationName == null) {
            destinationName = endpoint.getDestinationName();
        }

        final String to = destinationName;

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
                        jmsReplyTo = exchange.getIn().getHeader(SjmsConstants.JMS_REPLY_TO, String.class);
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
                    LOG.debug(
                            "Disabling JMSReplyTo: {} for destination: {}. Use preserveMessageQos=true to force Camel to keep the JMSReplyTo on endpoint: {}",
                            jmsReplyTo, to, endpoint);

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
                String replyToOverride = endpoint.getReplyToOverride();
                if (replyToOverride != null) {
                    replyTo = resolveOrCreateDestination(replyToOverride, session);
                } else if (jmsReplyTo != null) {
                    replyTo = (Destination) jmsReplyTo;
                }
                if (replyTo != null) {
                    LOG.debug("Using JMSReplyTo destination: {}", replyTo);
                    JmsMessageHelper.setJMSReplyTo(answer, replyTo);
                } else {
                    // do not use JMSReplyTo
                    LOG.trace("Not using JMSReplyTo");
                    JmsMessageHelper.setJMSReplyTo(answer, null);
                }

                LOG.trace("Created jakarta.jms.Message: {}", answer);
                return answer;
            }
        };

        try {
            doSend(exchange, false, destinationName, messageCreator);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // after sending then set the OUT message id to the JMSMessageID so its identical
        setMessageId(exchange);

        // we are synchronous so return true
        callback.done(true);
        return true;
    }

    protected void setMessageId(Exchange exchange) {
        if (exchange.hasOut()) {
            SjmsMessage out = exchange.getOut(SjmsMessage.class);
            try {
                if (out != null && out.getJmsMessage() != null) {
                    out.setMessageId(out.getJmsMessage().getJMSMessageID());
                }
            } catch (JMSException e) {
                LOG.warn("Unable to retrieve JMSMessageID from outgoing JMS Message and set it into Camel's MessageId", e);
            }
        }
    }

    public SjmsTemplate getInOnlyTemplate() {
        if (inOnlyTemplate == null) {
            inOnlyTemplate = endpoint.createInOnlyTemplate();
        }
        return inOnlyTemplate;
    }

    public void setInOnlyTemplate(SjmsTemplate inOnlyTemplate) {
        this.inOnlyTemplate = inOnlyTemplate;
    }

    public SjmsTemplate getInOutTemplate() {
        if (inOutTemplate == null) {
            inOutTemplate = endpoint.createInOutTemplate();
        }
        return inOutTemplate;
    }

    public void setInOutTemplate(SjmsTemplate inOutTemplate) {
        this.inOutTemplate = inOutTemplate;
    }

    public UuidGenerator getUuidGenerator() {
        return uuidGenerator;
    }

    public void setUuidGenerator(UuidGenerator uuidGenerator) {
        this.uuidGenerator = uuidGenerator;
    }

    protected Destination resolveOrCreateDestination(String destinationName, Session session)
            throws JMSException {

        boolean isPubSub = isTopicPrefix(destinationName)
                || !isQueuePrefix(destinationName) && endpoint.isTopic();

        // must normalize the destination name
        String before = destinationName;
        destinationName = normalizeDestinationName(destinationName);
        LOG.trace("Normalized JMSReplyTo destination name {} -> {}", before, destinationName);

        return endpoint.getDestinationCreationStrategy().createDestination(session, destinationName, isPubSub);
    }

    /**
     * Strategy to determine which correlation id to use among <tt>JMSMessageID</tt> and <tt>JMSCorrelationID</tt>.
     *
     * @param  message      the JMS message
     * @throws JMSException can be thrown
     */
    protected String determineCorrelationId(Message message) throws JMSException {
        String cid = getJMSCorrelationID(message);
        if (ObjectHelper.isEmpty(cid)) {
            cid = getJMSMessageID(message);
        }
        return cid;
    }

    /**
     * Sends the message using the JmsTemplate.
     *
     * @param exchange        the exchange
     * @param inOut           use inOut or inOnly template
     * @param destinationName the destination
     * @param messageCreator  the creator to create the {@link Message} to send
     */
    protected void doSend(
            Exchange exchange,
            boolean inOut, String destinationName,
            MessageCreator messageCreator)
            throws Exception {

        SjmsTemplate template = inOut ? getInOutTemplate() : getInOnlyTemplate();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Using {} jms template", inOut ? "inOut" : "inOnly");
        }

        template.send(exchange, destinationName, messageCreator, getEndpoint().isTopic());
    }

}
