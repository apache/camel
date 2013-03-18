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
package org.apache.camel.component.aws.sqs;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Amazon Web Service Simple Queue Service
 * <a href="http://aws.amazon.com/sqs/">AWS SQS</a>
 * 
 */
public class SqsProducer extends DefaultProducer {
    
    private static final transient Logger LOG = LoggerFactory.getLogger(SqsProducer.class);
    
    public SqsProducer(SqsEndpoint endpoint) throws NoFactoryAvailableException {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        String body = exchange.getIn().getBody(String.class);
        SendMessageRequest request = new SendMessageRequest(getQueueUrl(), body);
        request.setDelaySeconds(getEndpoint().getConfiguration().getDelaySeconds());

        LOG.trace("Sending request [{}] from exchange [{}]...", request, exchange);
        
        SendMessageResult result = getClient().sendMessage(request);
        
        LOG.trace("Received result [{}]", result);
        
        Message message = getMessageForResponse(exchange);
        message.setHeader(SqsConstants.MESSAGE_ID, result.getMessageId());
        message.setHeader(SqsConstants.MD5_OF_BODY, result.getMD5OfMessageBody());
    }

    private Message getMessageForResponse(Exchange exchange) {
        if (exchange.getPattern().isOutCapable()) {
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());
            return out;
        }
        
        return exchange.getIn();
    }
    
    protected AmazonSQS getClient() {
        return getEndpoint().getClient();
    }
    
    protected String getQueueUrl() {
        return getEndpoint().getQueueUrl();
    }
    
    @Override
    public SqsEndpoint getEndpoint() {
        return (SqsEndpoint) super.getEndpoint();
    }
    
    @Override
    public String toString() {
        return "SqsProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
    }
}
