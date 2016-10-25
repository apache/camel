package org.apache.camel.component.windowsazure.servicebus;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.ServiceBusContract;
import com.microsoft.windowsazure.services.servicebus.models.BrokeredMessage;
import org.apache.camel.Exchange;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Date;
import java.util.Map;

/**
 * Created by alan on 14/10/16.
 */
public abstract class AbstractSbProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractSbProducer.class);

    private transient String sbProducerToString;
    private void translateAttributes(BrokeredMessage brokeredMessage, Map<String, Object> headers) {

        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            switch (key){
                case SbConstants.MESSAGE_ID:
                    brokeredMessage.setMessageId((String)value);
                    break;
                case SbConstants.CONTENT_TYPE:
                    brokeredMessage.setContentType((String)value);
                    break;
                case SbConstants.CORRELATION_ID:
                    brokeredMessage.setCorrelationId((String)value);
                    break;
                case SbConstants.DATE:
                    brokeredMessage.setDate((Date)value);
                    break;
                case SbConstants.LABEL:
                    brokeredMessage.setLabel((String) value);
                    break;
                case SbConstants.PARTITION_KEY:
                    brokeredMessage.setPartitionKey((String) value);
                    break;
                case SbConstants.REPLY_TO:
                    brokeredMessage.setReplyTo((String) value);
                    break;
                case SbConstants.REPLY_TO_SESSION_ID:
                    brokeredMessage.setReplyToSessionId((String) value);
                    break;
                case SbConstants.SCHEDULED_ENQUEUE_TIME_UTC:
                    brokeredMessage.setScheduledEnqueueTimeUtc((Date) value);
                    break;
                case SbConstants.TIME_TO_LIVE:
                    brokeredMessage.setTimeToLive((Double) value);
                    break;
                case SbConstants.SESSION_ID:
                    brokeredMessage.setSessionId((String) value);
                    break;
                case SbConstants.TO:
                    brokeredMessage.setTo((String) value);
                    break;
                case SbConstants.VIA_PARTITION_KEY:
                    brokeredMessage.setViaPartitionKey((String) value);
                    break;
                default:
                    brokeredMessage.setProperty(key,value);
                    break;
            }
        }
    }
    public AbstractSbProducer(AbstractSbEndpoint abstractSbEndpoint) throws NoFactoryAvailableException {
        super(abstractSbEndpoint);
    }
    protected abstract void sendMessage(BrokeredMessage brokeredMessage) throws ServiceException;

    public void process(Exchange exchange) throws Exception {
        BrokeredMessage brokeredMessage;

        InputStream bodyStream = exchange.getIn().getBody(InputStream.class);
        if (null !=bodyStream) { //Prefer to input stream if possible
            brokeredMessage = new BrokeredMessage(bodyStream);
        }else {
            String bodyString = exchange.getIn().getBody(String.class);
            brokeredMessage = new BrokeredMessage(bodyString);
        }

        if(null != exchange.getIn().getMessageId()) {
            brokeredMessage.setMessageId(exchange.getIn().getMessageId());
        }
        //todo: attachments not supported.

        translateAttributes(brokeredMessage, exchange.getIn().getHeaders());

        LOG.trace("Sending request [{}] from exchange [{}]...", brokeredMessage, exchange);

        sendMessage(brokeredMessage);
    }

    protected ServiceBusContract getClient() {
        return getEndpoint().getClient();
    }

    @Override
    public AbstractSbEndpoint getEndpoint() {
        return (AbstractSbEndpoint) super.getEndpoint();
    }

    @Override
    public String toString() {
        if (sbProducerToString == null) {
            sbProducerToString = "AbstractSbProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return sbProducerToString;
    }

}
