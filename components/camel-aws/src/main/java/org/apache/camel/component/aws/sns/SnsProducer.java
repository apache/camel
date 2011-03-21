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
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A Producer which sends messages to the Amazon Web Service Simple Notification Service
 * <a href="http://aws.amazon.com/sns/">AWS SNS</a>
 */
public class SnsProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(SnsProducer.class);

    public SnsProducer(Endpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        PublishRequest request = new PublishRequest();
        request.setTopicArn(getConfiguration().getTopicArn());
        request.setMessage(exchange.getIn().getBody(String.class));
        request.setSubject(determineSubject(exchange));
        
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
    
    protected SnsConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }
    
    @Override
    public String toString() {
        return "SnsProducer[" + DefaultEndpoint.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
    }
    
    @Override
    public SnsEndpoint getEndpoint() {
        return (SnsEndpoint) super.getEndpoint();
    }
}