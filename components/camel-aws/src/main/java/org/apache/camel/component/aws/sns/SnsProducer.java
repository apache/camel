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
package org.apache.camel.component.aws.sns;

import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A Producer which sends messages to the Amazon Web Service Simple Notification Service
 * <a href="http://aws.amazon.com/sns/">AWS SNS</a>
 */
public class SnsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SnsProducer.class);
    
    private transient String snsProducerToString;

    public SnsProducer(Endpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        PublishRequest request = new PublishRequest();

        request.setTopicArn(getConfiguration().getTopicArn());
        request.setSubject(determineSubject(exchange));
        request.setMessageStructure(determineMessageStructure(exchange));
        request.setMessage(exchange.getIn().getBody(String.class));

        LOG.trace("Sending request [{}] from exchange [{}]...", request, exchange);
        
        PublishResult result = getEndpoint().getSNSClient().publish(request);

        LOG.trace("Received result [{}]", result);
        
        Message message = getMessageForResponse(exchange);
        message.setHeader(SnsConstants.MESSAGE_ID, result.getMessageId());
    }
    
    private Message getMessageForResponse(Exchange exchange) {
        if (exchange.getPattern().isOutCapable()) {
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());
            return out;
        }
        
        return exchange.getIn();
    }

    private String determineSubject(Exchange exchange) {
        String subject = exchange.getIn().getHeader(SnsConstants.SUBJECT, String.class);
        if (subject == null) {
            subject = getConfiguration().getSubject();
        }
        
        return subject;
    }

    private String determineMessageStructure(Exchange exchange) {
        String structure = exchange.getIn().getHeader(SnsConstants.MESSAGE_STRUCTURE, String.class);
        if (structure == null) {
            structure = getConfiguration().getMessageStructure();
        }

        return structure;
    }
    
    protected SnsConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }
    
    @Override
    public String toString() {
        if (snsProducerToString == null) {
            snsProducerToString = "SnsProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return snsProducerToString;
    }
    
    @Override
    public SnsEndpoint getEndpoint() {
        return (SnsEndpoint) super.getEndpoint();
    }
}