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

import java.io.InputStream;
import java.util.Date;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.URISupport;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.ServiceBusContract;
import com.microsoft.windowsazure.services.servicebus.models.BrokeredMessage;

public abstract class AbstractSbProducer extends DefaultProducer {

    private transient String sbProducerToString;

    public AbstractSbProducer(AbstractSbEndpoint abstractSbEndpoint) throws NoFactoryAvailableException {
        super(abstractSbEndpoint);
    }

    private void translateAttributes(BrokeredMessage brokeredMessage, Map<String, Object> headers) {

        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            switch (key) {
            case SbConstants.MESSAGE_ID:
                brokeredMessage.setMessageId((String)value);
                break;
            case SbConstants.CONTENT_TYPE:
                // FIXME: just passing through a content_type is dangerous
                brokeredMessage.setContentType(SbConstants.DEFAULT_CONTENT_TYPE);
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
            }
        }
    }

    protected abstract void sendMessage(BrokeredMessage brokeredMessage) throws ServiceException;

    public void process(Exchange exchange) throws Exception {
        BrokeredMessage brokeredMessage;

        InputStream bodyStream = exchange.getIn().getBody(InputStream.class);
        if (null != bodyStream) { //Prefer to input stream if possible
            brokeredMessage = new BrokeredMessage(bodyStream);
        } else {
            String bodyString = exchange.getIn().getBody(String.class);
            brokeredMessage = new BrokeredMessage(bodyString);
        }

        if (null != exchange.getIn().getMessageId()) {
            brokeredMessage.setMessageId(exchange.getIn().getMessageId());
        }
        //todo: attachments not supported.

        translateAttributes(brokeredMessage, exchange.getIn().getHeaders());

        log.trace("Sending request [{}] from exchange [{}]...", brokeredMessage, exchange);

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
