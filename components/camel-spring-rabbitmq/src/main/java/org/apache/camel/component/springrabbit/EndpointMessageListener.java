package org.apache.camel.component.springrabbit;

import com.rabbitmq.client.Channel;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.RollbackExchangeException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

import static org.apache.camel.RuntimeCamelException.wrapRuntimeCamelException;

public class EndpointMessageListener implements ChannelAwareMessageListener {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointMessageListener.class);

    private final RabbitMQEndpoint endpoint;
    private final AsyncProcessor processor;
    private boolean disableReplyTo;
    private boolean async;

    public EndpointMessageListener(RabbitMQEndpoint endpoint, Processor processor) {
        this.endpoint = endpoint;
        this.processor = AsyncProcessorConverterHelper.convert(processor);
    }

    public boolean isAsync() {
        return async;
    }

    /**
     * Sets whether asynchronous routing is enabled.
     * <p/>
     * By default this is <tt>false</tt>. If configured as <tt>true</tt> then this listener will process the
     * {@link org.apache.camel.Exchange} asynchronous.
     */
    public void setAsync(boolean async) {
        this.async = async;
    }

    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        LOG.trace("onMessage START");

        LOG.debug("{} consumer received RabbitMQ message: {}", endpoint, message);
        boolean sendReply;
        RuntimeCamelException rce;
        try {
            Object replyDestination = null;
            final Exchange exchange = createExchange(message, channel, replyDestination);

            // TODO: request/reply
            sendReply = false;

            // process the exchange either asynchronously or synchronous
            LOG.trace("onMessage.process START");
            AsyncCallback callback
                    = new EndpointMessageListenerAsyncCallback(message, exchange, endpoint, sendReply, replyDestination);

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

        } catch (Exception e) {
            rce = wrapRuntimeCamelException(e);
        }

        // an exception occurred so rethrow to trigger rollback on listener
        // the listener will use the error handler to handle the uncaught exception
        if (rce != null) {
            LOG.trace("onMessage END throwing exception: {}", rce.getMessage());
            // Spring message listener container will handle uncaught exceptions
            throw rce;
        }

        LOG.trace("onMessage END");
    }

    protected Exchange createExchange(Message message, Channel channel, Object replyDestination) {
        Exchange exchange = endpoint.createExchange(message);
        exchange.setProperty(RabbitMQConstants.CHANNEL, channel);

        // lets set to an InOut if we have some kind of reply-to destination
        if (replyDestination != null && !disableReplyTo) {
            // only change pattern if not already out capable
            if (!exchange.getPattern().isOutCapable()) {
                exchange.setPattern(ExchangePattern.InOut);
            }
        }
        return exchange;
    }

    /**
     * Callback task that is performed when the exchange has been processed
     */
    private final class EndpointMessageListenerAsyncCallback implements AsyncCallback {

        private final Message message;
        private final Exchange exchange;
        private final RabbitMQEndpoint endpoint;
        private final boolean sendReply;
        private final Object replyDestination;

        private EndpointMessageListenerAsyncCallback(Message message, Exchange exchange, RabbitMQEndpoint endpoint,
                                                     boolean sendReply, Object replyDestination) {
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
                    // only throw exception if endpoint is not configured to transfer exceptions back to caller
                    // do not send a reply but wrap and rethrow the exception
                    rce = wrapRuntimeCamelException(exchange.getException());
                }
            } else {
                // process OK so get the reply body if we are InOut and has a body
                // If the ppl don't want to send the message back, he should use the InOnly
                if (sendReply && exchange.getPattern().isOutCapable()) {
                    if (exchange.hasOut()) {
                        body = exchange.getOut();
                    } else {
                        body = exchange.getIn();
                    }
                    cause = null;
                }
            }

            // send back reply if there was no error and we are supposed to send back a reply
            if (rce == null && sendReply && (body != null || cause != null)) {
                LOG.trace("onMessage.sendReply START");
                // TODO: reply
                /*if (replyDestination instanceof Destination) {
                    sendReply((Destination) replyDestination, message, exchange, body, cause);
                } else {
                    sendReply((String) replyDestination, message, exchange, body, cause);
                }*/
                LOG.trace("onMessage.sendReply END");
            }

            // if an exception occurred
            if (rce != null) {
                if (doneSync) {
                    // we were done sync, so put exception on exchange, so we can grab it in the onMessage
                    // method and rethrow it
                    exchange.setException(rce);
                } else {
                    // we were done async, so use the endpoint error handler
                    if (endpoint.getExceptionHandler() != null) {
                        endpoint.getExceptionHandler().handleException(rce);
                    }
                }
            }
        }
    }

}
