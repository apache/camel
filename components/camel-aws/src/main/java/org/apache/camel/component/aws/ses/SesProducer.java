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
package org.apache.camel.component.aws.ses;

import java.util.Collection;
import java.util.List;

import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.amazonaws.services.simpleemail.model.SendEmailResult;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.URISupport;

/**
 * A Producer which sends messages to the Amazon Simple Email Service
 * <a href="http://aws.amazon.com/ses/">AWS SES</a>
 */
public class SesProducer extends DefaultProducer {
    
    public SesProducer(Endpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        SendEmailRequest request = createMailRequest(exchange);
        log.trace("Sending request [{}] from exchange [{}]...", request, exchange);
        
        SendEmailResult result = getEndpoint().getSESClient().sendEmail(request);

        log.trace("Received result [{}]", result);
        Message message = getMessageForResponse(exchange);
        message.setHeader(SesConstants.MESSAGE_ID, result.getMessageId());
    }

    private SendEmailRequest createMailRequest(Exchange exchange) {
        SendEmailRequest request = new SendEmailRequest();
        request.setSource(determineFrom(exchange));
        request.setDestination(determineTo(exchange));
        request.setReturnPath(determineReturnPath(exchange));
        request.setReplyToAddresses(determineReplyToAddresses(exchange));
        request.setMessage(createMessage(exchange));

        return request;
    }

    private com.amazonaws.services.simpleemail.model.Message createMessage(Exchange exchange) {
        com.amazonaws.services.simpleemail.model.Message message = new com.amazonaws.services.simpleemail.model.Message();
        Boolean isHtmlEmail = exchange.getIn().getHeader(SesConstants.HTML_EMAIL, false, Boolean.class);
        String content = exchange.getIn().getBody(String.class);
        if (isHtmlEmail) {
            message.setBody(new Body().withHtml(new Content().withData(content)));
        } else {
            message.setBody(new Body().withText(new Content().withData(content)));
        }
        message.setSubject(new Content(determineSubject(exchange)));
        return message;
    }
    
    @SuppressWarnings("unchecked")
    private Collection<String> determineReplyToAddresses(Exchange exchange) {
        List<String> replyToAddresses = exchange.getIn().getHeader(SesConstants.REPLY_TO_ADDRESSES, List.class);
        if (replyToAddresses == null) {
            replyToAddresses = getConfiguration().getReplyToAddresses();
        }
        return replyToAddresses;
    }
    
    private String determineReturnPath(Exchange exchange) {
        String returnPath = exchange.getIn().getHeader(SesConstants.RETURN_PATH, String.class);
        if (returnPath == null) {
            returnPath = getConfiguration().getReturnPath();
        }
        return returnPath;
    }

    @SuppressWarnings("unchecked")
    private Destination determineTo(Exchange exchange) {
        List<String> to = exchange.getIn().getHeader(SesConstants.TO, List.class);
        if (to == null) {
            to = getConfiguration().getTo();
        }
        return new Destination(to);
    }

    private String determineFrom(Exchange exchange) {
        String from = exchange.getIn().getHeader(SesConstants.FROM, String.class);
        if (from == null) {
            from = getConfiguration().getFrom();
        }
        return from;
    }

    private String determineSubject(Exchange exchange) {
        String subject = exchange.getIn().getHeader(SesConstants.SUBJECT, String.class);
        if (subject == null) {
            subject = getConfiguration().getSubject();
        }
        return subject;
    }

    private Message getMessageForResponse(Exchange exchange) {
        if (exchange.getPattern().isOutCapable()) {
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());
            return out;
        }
        return exchange.getIn();
    }

    protected SesConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        return "SesProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
    }

    @Override
    public SesEndpoint getEndpoint() {
        return (SesEndpoint) super.getEndpoint();
    }
}
