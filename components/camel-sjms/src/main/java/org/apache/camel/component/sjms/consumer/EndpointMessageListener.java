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
package org.apache.camel.component.sjms.consumer;

import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.RollbackExchangeException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.sjms.SessionCallback;
import org.apache.camel.component.sjms.SessionMessageListener;
import org.apache.camel.component.sjms.SjmsConstants;
import org.apache.camel.component.sjms.SjmsConsumer;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.component.sjms.SjmsMessage;
import org.apache.camel.component.sjms.SjmsTemplate;
import org.apache.camel.component.sjms.jms.JmsMessageHelper;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.RuntimeCamelException.wrapRuntimeCamelException;

/**
 * A JMS {@link MessageListener} which can be used to delegate processing to a Camel endpoint.
 *
 * Note that instance of this object has to be thread safe (reentrant)
 */
public class EndpointMessageListener implements SessionMessageListener {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointMessageListener.class);

    private final SjmsConsumer consumer;
    private final SjmsEndpoint endpoint;
    private final AsyncProcessor processor;
    private Object replyToDestination;
    private boolean disableReplyTo;
    private boolean async;
    private boolean eagerLoadingOfProperties;
    private String eagerPoisonBody;
    private volatile SjmsTemplate template;

    public EndpointMessageListener(SjmsConsumer consumer, SjmsEndpoint endpoint, Processor processor) {
        this.consumer = consumer;
        this.endpoint = endpoint;
        this.processor = AsyncProcessorConverterHelper.convert(processor);
    }

    public synchronized SjmsTemplate getTemplate() {
        if (template == null) {
            template = endpoint.createInOnlyTemplate();
        }
        return template;
    }

    public void setTemplate(SjmsTemplate template) {
        this.template = template;
    }

    public Object getReplyToDestination() {
        return replyToDestination;
    }

    public void setReplyToDestination(Object replyToDestination) {
        this.replyToDestination = replyToDestination;
    }

    public boolean isDisableReplyTo() {
        return disableReplyTo;
    }

    public void setDisableReplyTo(boolean disableReplyTo) {
        this.disableReplyTo = disableReplyTo;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public boolean isEagerLoadingOfProperties() {
        return eagerLoadingOfProperties;
    }

    public void setEagerLoadingOfProperties(boolean eagerLoadingOfProperties) {
        this.eagerLoadingOfProperties = eagerLoadingOfProperties;
    }

    public String getEagerPoisonBody() {
        return eagerPoisonBody;
    }

    public void setEagerPoisonBody(String eagerPoisonBody) {
        this.eagerPoisonBody = eagerPoisonBody;
    }

    @Override
    public void onMessage(Message message, Session session) {
        LOG.trace("onMessage START");

        LOG.debug("{} consumer received JMS message: {}", endpoint, message);

        boolean sendReply;
        RuntimeCamelException rce;
        try {
            Object replyDestination = getReplyToDestination(message);
            // we can only send back a reply if there was a reply destination configured
            // and disableReplyTo hasn't been explicit enabled
            sendReply = replyDestination != null && !disableReplyTo;

            // we should also not send back reply to ourself if this destination and replyDestination is the same
            Destination destination = JmsMessageHelper.getJMSDestination(message);
            if (destination != null && sendReply && !endpoint.isReplyToSameDestinationAllowed()
                    && destination.equals(replyDestination)) {
                LOG.debug("JMSDestination and JMSReplyTo is the same, will skip sending a reply message to itself: {}",
                        destination);
                sendReply = false;
            }

            final Exchange exchange = createExchange(message, session, replyDestination);
            if (ObjectHelper.isNotEmpty(eagerPoisonBody) && eagerLoadingOfProperties) {
                try {
                    exchange.getIn().getBody();
                    exchange.getIn().getHeaders();
                } catch (Exception e) {
                    // any problems with eager loading then set an exception so Camel error handler can react
                    exchange.setException(e);
                    String text = eagerPoisonBody;
                    try {
                        text = endpoint.getCamelContext().resolveLanguage("simple")
                                .createExpression(eagerPoisonBody).evaluate(exchange, String.class);
                    } catch (Exception t) {
                        // ignore
                    }
                    exchange.getIn().setBody(text);
                }
            } else if (eagerLoadingOfProperties) {
                exchange.getIn().getBody();
                exchange.getIn().getHeaders();
            }

            String correlationId = message.getJMSCorrelationID();
            if (correlationId != null) {
                LOG.debug("Received Message has JMSCorrelationID [{}]", correlationId);
            }

            // process the exchange either asynchronously or synchronous
            LOG.trace("onMessage.process START");
            AsyncCallback callback
                    = new EndpointMessageListenerAsyncCallback(
                            session, message, exchange, endpoint, sendReply, replyDestination);

            // async is by default false, which mean we by default will process the exchange synchronously
            // to keep backwards compatible, as well ensure this consumer will pickup messages in order
            // (eg to not consume the next message before the previous has been fully processed)
            // but if end user explicit configure consumerAsync=true, then we can process the message
            // asynchronously (unless endpoint has been configured synchronous, or we use transaction)
            boolean forceSync = endpoint.isSynchronous() || endpoint.isTransacted();
            if (forceSync || !isAsync()) {
                // must process synchronous if transacted or configured to do so
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing exchange {} synchronously", exchange.getExchangeId());
                }
                try {
                    processor.process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                } finally {
                    callback.done(true);
                }
            } else {
                // process asynchronous using the async routing engine
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing exchange {} asynchronously", exchange.getExchangeId());
                }
                boolean sync = processor.process(exchange, callback);
                if (!sync) {
                    // will be done async so return now
                    return;
                }
            }
            // if we failed processed the exchange from the async callback task, then grab the exception
            rce = exchange.getException(RuntimeCamelException.class);

            // release back when synchronous mode
            consumer.releaseExchange(exchange, false);
        } catch (Exception e) {
            rce = wrapRuntimeCamelException(e);
        }

        // an exception occurred so rethrow to trigger rollback on JMS listener
        // the JMS listener will use the error handler to handle the uncaught exception
        if (rce != null) {
            LOG.trace("onMessage END throwing exception: {}", rce.getMessage());
            // Spring message listener container will handle uncaught exceptions
            // being thrown from this onMessage, and will us the ErrorHandler configured
            // on the JmsEndpoint to handle the exception
            throw rce;
        }

        LOG.trace("onMessage END");
    }

    protected Object getReplyToDestination(Message message) {
        // lets send a response back if we can
        Object destination = getReplyToDestination();
        if (destination == null) {
            destination = JmsMessageHelper.getJMSReplyTo(message);
        }
        return destination;
    }

    public Exchange createExchange(Message message, Session session, Object replyDestination) {
        Exchange exchange = consumer.createExchange(false);
        // reuse existing jms message if pooled
        org.apache.camel.Message msg = exchange.getIn();
        if (msg instanceof SjmsMessage) {
            SjmsMessage jm = (SjmsMessage) msg;
            jm.init(exchange, message, session, endpoint.getBinding());
        } else {
            exchange.setIn(new SjmsMessage(exchange, message, session, endpoint.getBinding()));
        }

        // lets set to an InOut if we have some kind of reply-to destination
        if (replyDestination != null && !disableReplyTo) {
            // only change pattern if not already out capable
            if (!exchange.getPattern().isOutCapable()) {
                exchange.setPattern(ExchangePattern.InOut);
            }
        }

        // store session on exchange as we may need it for transactions support
        exchange.setProperty(SjmsConstants.JMS_SESSION, session);

        return exchange;
    }

    protected void sendReply(
            Session session,
            Destination replyDestination, final Message message, final Exchange exchange,
            final org.apache.camel.Message out, final Exception cause) {
        if (replyDestination == null) {
            LOG.debug("Cannot send reply message as there is no replyDestination for: {}", out);
            return;
        }
        try {
            SessionCallback callback = createSessionCallback(replyDestination, message, exchange, out, cause,
                    endpoint.getJmsObjectFactory()::createMessageProducer);

            getTemplate().execute(session, callback);

        } catch (Exception e) {
            exchange.setException(new CamelExchangeException("Unable to send reply JMS message", exchange, e));
        }
    }

    @FunctionalInterface
    private interface MessageProducerCreator<T> {
        MessageProducer create(Session session, Endpoint endpoint, T replyDestination) throws Exception;
    }

    private <T> SessionCallback createSessionCallback(
            T replyDestination, Message message, Exchange exchange, org.apache.camel.Message out, Exception cause,
            MessageProducerCreator<T> messageProducerCreator) {
        return new SessionCallback() {
            @Override
            public Object doInJms(Session session) throws Exception {
                MessageProducer producer = null;
                try {
                    Message reply = endpoint.getBinding().makeJmsMessage(exchange, out, session, cause);
                    final String correlationID = determineCorrelationId(message);
                    reply.setJMSCorrelationID(correlationID);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("{} sending reply JMS message [correlationId:{}]: {}", endpoint, correlationID, reply);
                    }

                    producer = messageProducerCreator.create(session, endpoint, replyDestination);

                    template.send(producer, reply);
                } finally {
                    close(producer);
                }

                return null;
            }

            @Override
            public void onClose(Connection connection, Session session) {
                // do not close as we use provided session
            }
        };
    }

    protected void sendReply(
            Session session,
            String replyDestination, final Message message, final Exchange exchange,
            final org.apache.camel.Message out, final Exception cause) {
        if (replyDestination == null) {
            LOG.debug("Cannot send reply message as there is no replyDestination for: {}", out);
            return;
        }
        try {
            SessionCallback callback = createSessionCallback(replyDestination, message, exchange, out, cause,
                    endpoint.getJmsObjectFactory()::createMessageProducer);

            getTemplate().execute(session, callback);

        } catch (Exception e) {
            exchange.setException(new CamelExchangeException("Unable to send reply JMS message", exchange, e));
        }
    }

    /**
     * Strategy to determine which correlation id to use among <tt>JMSMessageID</tt> and <tt>JMSCorrelationID</tt>.
     *
     * @param  message      the JMS message
     * @return              the correlation id to use
     * @throws JMSException can be thrown
     */
    protected String determineCorrelationId(final Message message) throws JMSException {
        final String messageId = message.getJMSMessageID();
        final String correlationId = message.getJMSCorrelationID();

        if (ObjectHelper.isEmpty(correlationId)) {
            // correlation id is empty so fallback to message id
            return messageId;
        } else {
            return correlationId;
        }
    }

    protected static void close(MessageProducer producer) {
        if (producer != null) {
            try {
                producer.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    /**
     * Callback task that is performed when the exchange has been processed
     */
    private final class EndpointMessageListenerAsyncCallback implements AsyncCallback {

        private final Session session;
        private final Message message;
        private final Exchange exchange;
        private final SjmsEndpoint endpoint;
        private final boolean sendReply;
        private final Object replyDestination;

        private EndpointMessageListenerAsyncCallback(Session session, Message message, Exchange exchange, SjmsEndpoint endpoint,
                                                     boolean sendReply, Object replyDestination) {
            this.session = session;
            this.message = message;
            this.exchange = exchange;
            this.endpoint = endpoint;
            this.sendReply = sendReply;
            this.replyDestination = replyDestination;
        }

        @Override
        public void done(boolean doneSync) {
            LOG.trace("onMessage.process END");

            // now we evaluate the processing of the exchange and determine if it was a success or failure
            // we also grab information from the exchange to be used for sending back a reply (if we are to do so)
            // so the following logic seems a bit complicated at first glance

            // if we send back a reply it can either be the message body or transferring a caused exception
            org.apache.camel.Message body = null;
            Exception cause = null;
            RuntimeCamelException rce = null;

            if (exchange.isFailed() || exchange.isRollbackOnly()) {
                if (exchange.isRollbackOnly()) {
                    // rollback only so wrap an exception so we can rethrow the exception to cause rollback
                    rce = wrapRuntimeCamelException(new RollbackExchangeException(exchange));
                } else if (exchange.getException() != null) {
                    // an exception occurred while processing
                    if (endpoint.isTransferException()) {
                        // send the exception as reply, so null body and set the exception as the cause
                        body = null;
                        cause = exchange.getException();
                    } else {
                        // only throw exception if endpoint is not configured to transfer exceptions back to caller
                        // do not send a reply but wrap and rethrow the exception
                        rce = wrapRuntimeCamelException(exchange.getException());
                    }
                }
            } else {
                // process OK so get the reply body if we are InOut and has a body
                // If the ppl don't want to send the message back, he should use the InOnly
                if (sendReply && exchange.getPattern().isOutCapable()) {
                    body = exchange.getMessage();
                    cause = null;
                }
            }

            // send back reply if there was no error and we are supposed to send back a reply
            if (rce == null && sendReply && (body != null || cause != null)) {
                LOG.trace("onMessage.sendReply START");
                if (replyDestination instanceof Destination) {
                    sendReply(session, (Destination) replyDestination, message, exchange, body, cause);
                } else {
                    sendReply(session, (String) replyDestination, message, exchange, body, cause);
                }
                LOG.trace("onMessage.sendReply END");
            }

            // if an exception occurred
            if (rce != null) {
                if (doneSync) {
                    // we were done sync, so put exception on exchange, so we can grab it in the onMessage
                    // method and rethrow it
                    exchange.setException(rce);
                } else {
                    // we were done async, so use the exception handler
                    if (endpoint.getExceptionHandler() != null) {
                        endpoint.getExceptionHandler().handleException(rce);
                    }
                }
            }

            if (!doneSync) {
                // release back when in asynchronous mode
                consumer.releaseExchange(exchange, false);
            }
        }
    }

}
