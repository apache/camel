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
package org.apache.camel.component.azure.servicebus;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.LoggingExceptionHandler;
import org.apache.camel.util.URISupport;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.ServiceBusContract;
import com.microsoft.windowsazure.services.servicebus.models.BrokeredMessage;
import com.microsoft.windowsazure.services.servicebus.models.ReceiveMessageOptions;

public abstract class AbstractSbConsumer extends ScheduledPollConsumer {

    private String sbConsumerToString;

    AbstractSbConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
        setExceptionHandler(new LoggingExceptionHandler(endpoint.getCamelContext(), getClass(), LoggingLevel.ERROR));
    }

    protected SbConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    protected ServiceBusContract getClient() {
        return getEndpoint().getClient();
    }

    /**
     * Strategy to delete the message after being processed.
     *
     * @param exchange the exchange
     */
    protected void processCommit(Exchange exchange) {
        try {
            if (getConfiguration().isPeekLock()) {
                BrokeredMessage delMsg = getLockBrokeredMessage(exchange);
                getClient().deleteMessage(delMsg);
                log.debug("delete message - LOCK_LOCATION: " + exchange.getIn().getHeader(SbConstants.LOCK_LOCATION, String.class));
                log.debug("delete message - LOCK_TOKEN: " + exchange.getIn().getHeader(SbConstants.LOCK_TOKEN, String.class));
            }
        } catch (ServiceException e) {
            getExceptionHandler().handleException("Error occurred during deleting message from the Service Bus. This exception is ignored.", exchange, e);
        }
    }

    private BrokeredMessage getLockBrokeredMessage(Exchange exchange) {
        BrokeredMessage delMsg = new BrokeredMessage();
        delMsg.getBrokerProperties().setLockLocation((String)exchange.getIn().getHeader(SbConstants.LOCK_LOCATION));
        delMsg.getBrokerProperties().setLockToken((String)exchange.getIn().getHeader(SbConstants.LOCK_TOKEN));
        return delMsg;
    }

    protected void processRollback(Exchange exchange) {
        if (getConfiguration().isPeekLock()) {
            try {
                getClient().unlockMessage(getLockBrokeredMessage(exchange));
                log.debug("unlock message - LOCK_LOCATION: " + exchange.getIn().getHeader(SbConstants.LOCK_LOCATION, String.class));
                log.debug("unlock message - LOCK_TOKEN: " + exchange.getIn().getHeader(SbConstants.LOCK_TOKEN, String.class));
            } catch (ServiceException e) {
                // do nothing. Because it will be unlock after timeout anyway.
                log.debug("failed unlocking a message", e);
            }
        }

        String errorMessage = String.format("Failed to deliver message with ASB MessageID %s from ExchangeID %s. Exception cause was %s", 
    			exchange.getIn().getHeader("CamelAzureMessageId"),
    			exchange.getIn().getMessageId(),
    			exchange.getException().getCause());

    	getExceptionHandler().handleException(errorMessage, exchange, exchange.getException());

    }

    /**
     * The polling method which is invoked periodically to poll this consumer
     *
     * @return number of messages polled, will be <tt>0</tt> if no message was polled at all.
     * @throws Exception can be thrown if an exception occurred during polling
     */
    @Override
    protected int poll() throws Exception {
        ReceiveMessageOptions opts = ReceiveMessageOptions.DEFAULT;

        if (getConfiguration().isPeekLock()) {
            opts.setPeekLock();
        }

        if (null != getConfiguration().getTimeout()) {
            opts.setTimeout(getConfiguration().getTimeout());
        }

        BrokeredMessage message = pollBrokeredMessage(opts);

        if (message != null && message.getMessageId() != null) {
            Exchange exchange = getEndpoint().createExchange(message);

            // add on completion to handle after work when the exchange is done
            exchange.addOnCompletion(new Synchronization() {
                public void onComplete(Exchange exchange) {
                    processCommit(exchange);
                }

                public void onFailure(Exchange exchange) {
                    processRollback(exchange);
                }

                @Override
                public String toString() {
                    return "SbConsumerOnCompletion";
                }
            });

            log.debug("Processing exchange [{}]...", exchange);
            getAsyncProcessor().process(exchange, doneSync -> log.debug("Processing exchange [{}] done.", exchange));

            return 1;
        } else {
            return 0;
        }


    }

    protected abstract BrokeredMessage pollBrokeredMessage(ReceiveMessageOptions opts) throws ServiceException;

    @Override
    public AbstractSbEndpoint getEndpoint() {
        return (AbstractSbEndpoint) super.getEndpoint();
    }

    @Override
    public String toString() {
        return sbConsumerToString == null
                ? sbConsumerToString = "AbstractSbConsumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]"
                : sbConsumerToString;
    }
}
