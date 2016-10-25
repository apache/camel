package org.apache.camel.component.windowsazure.servicebus;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.ServiceBusContract;
import com.microsoft.windowsazure.services.servicebus.models.BrokeredMessage;
import com.microsoft.windowsazure.services.servicebus.models.ReceiveMessageOptions;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Created by alan on 14/10/16.
 */
public abstract class AbstractSbConsumer extends ScheduledPollConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractSbConsumer.class);
    private transient String sbConsumerToString;
    private Collection<String> attributeNames;
    private Collection<String> messageAttributeNames;

//    private boolean shouldDelete(Exchange exchange) {
//        return getConfiguration().isPeekLock();
////        return exchange.getProperty(Exchange.FILTER_MATCHED) != null
////                && getConfiguration().isPeekLock()
////                && exchange.getProperty(Exchange.FILTER_MATCHED, false, Boolean.class);
//    }
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
                System.out.println("$$$$$$ delete message $$$$$$ LOCK_LOCATION: " + (String)exchange.getIn().getHeader(SbConstants.LOCK_LOCATION));
                System.out.println("$$$$$$ delete message $$$$$$ LOCK_TOKEN: " + (String)exchange.getIn().getHeader(SbConstants.LOCK_TOKEN));

            }
        } catch (ServiceException e) {
            getExceptionHandler().handleException("Error occurred during deleting message. This exception is ignored.", exchange, e);
        }
    }

    private BrokeredMessage getLockBrokeredMessage(Exchange exchange) {
        BrokeredMessage delMsg = new BrokeredMessage();
        delMsg.getBrokerProperties().setLockLocation((String)exchange.getIn().getHeader(SbConstants.LOCK_LOCATION));
        delMsg.getBrokerProperties().setLockToken((String)exchange.getIn().getHeader(SbConstants.LOCK_TOKEN));
        return delMsg;
    }

    protected void processRollback(Exchange exchange) {

        if (getConfiguration().isPeekLock()){
            try {
                getClient().unlockMessage(getLockBrokeredMessage(exchange));
                System.out.println("$$$$$$ unlock message $$$$$$ LOCK_LOCATION: " + (String)exchange.getIn().getHeader(SbConstants.LOCK_LOCATION));
                System.out.println("$$$$$$ unlock message $$$$$$ LOCK_TOKEN: " + (String)exchange.getIn().getHeader(SbConstants.LOCK_TOKEN));
            } catch (ServiceException e) {
                // do nothing. Because it will be unlock after timeout anyway.
            }
        }
        Exception cause = exchange.getException();
        if (cause != null) {
            getExceptionHandler().handleException("Error during processing exchange. Will attempt to process the message on next poll.", exchange, cause);
        }
    }

    public AbstractSbConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);

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
        if (getConfiguration().isPeekLock()){
            opts.setPeekLock();
        }
        if (null != getConfiguration().getTimeout()) {
            opts.setTimeout(getConfiguration().getTimeout());
        }

        BrokeredMessage message = pollBrokeredMessage(opts);

        if(message != null && message.getMessageId() != null){
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

            LOG.trace("Processing exchange [{}]...", exchange);
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    LOG.trace("Processing exchange [{}] done.", exchange);
                }
            });
            return 1;
        }else{
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
        if (sbConsumerToString == null) {
            sbConsumerToString = "AbstractSbConsumer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return sbConsumerToString;
    }
}
