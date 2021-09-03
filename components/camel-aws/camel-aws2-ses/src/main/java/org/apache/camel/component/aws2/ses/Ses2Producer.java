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
package org.apache.camel.component.aws2.ses;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;
import software.amazon.awssdk.services.ses.model.SendRawEmailResponse;

/**
 * A Producer which sends messages to the Amazon Simple Email Service SDK v2 <a href="http://aws.amazon.com/ses/">AWS
 * SES</a>
 */
public class Ses2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Ses2Producer.class);

    private transient String sesProducerToString;

    public Ses2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (!(exchange.getIn().getBody() instanceof javax.mail.Message)) {
            SendEmailRequest request = createMailRequest(exchange);
            LOG.trace("Sending request [{}] from exchange [{}]...", request, exchange);
            SendEmailResponse result = getEndpoint().getSESClient().sendEmail(request);
            LOG.trace("Received result [{}]", result);
            Message message = getMessageForResponse(exchange);
            message.setHeader(Ses2Constants.MESSAGE_ID, result.messageId());
        } else {
            SendRawEmailRequest request = createRawMailRequest(exchange);
            LOG.trace("Sending request [{}] from exchange [{}]...", request, exchange);
            SendRawEmailResponse result = getEndpoint().getSESClient().sendRawEmail(request);
            LOG.trace("Received result [{}]", result);
            Message message = getMessageForResponse(exchange);
            message.setHeader(Ses2Constants.MESSAGE_ID, result.messageId());
        }
    }

    private SendEmailRequest createMailRequest(Exchange exchange) {
        SendEmailRequest.Builder request = SendEmailRequest.builder();
        request.source(determineFrom(exchange));
        request.destination(determineTo(exchange));
        request.returnPath(determineReturnPath(exchange));
        request.replyToAddresses(determineReplyToAddresses(exchange));
        request.message(createMessage(exchange));

        return request.build();
    }

    private SendRawEmailRequest createRawMailRequest(Exchange exchange) throws Exception {
        SendRawEmailRequest.Builder request = SendRawEmailRequest.builder();
        request.source(determineFrom(exchange));
        request.destinations(determineRawTo(exchange));
        request.rawMessage(createRawMessage(exchange));
        return request.build();
    }

    private software.amazon.awssdk.services.ses.model.Message createMessage(Exchange exchange) {
        software.amazon.awssdk.services.ses.model.Message.Builder message
                = software.amazon.awssdk.services.ses.model.Message.builder();
        Boolean isHtmlEmail = exchange.getIn().getHeader(Ses2Constants.HTML_EMAIL, false, Boolean.class);
        String content = exchange.getIn().getBody(String.class);
        if (isHtmlEmail) {
            message.body(Body.builder().html(Content.builder().data(content).build()).build());
        } else {
            message.body(Body.builder().text(Content.builder().data(content).build()).build());
        }
        message.subject(Content.builder().data(determineSubject(exchange)).build());
        return message.build();
    }

    private software.amazon.awssdk.services.ses.model.RawMessage createRawMessage(Exchange exchange) throws Exception {
        software.amazon.awssdk.services.ses.model.RawMessage.Builder message
                = software.amazon.awssdk.services.ses.model.RawMessage.builder();
        javax.mail.Message content = exchange.getIn().getBody(javax.mail.Message.class);
        OutputStream byteOutput = new ByteArrayOutputStream();
        try {
            content.writeTo(byteOutput);
        } catch (Exception e) {
            LOG.error("Cannot write to byte Array");
            throw e;
        }
        byte[] messageByteArray = ((ByteArrayOutputStream) byteOutput).toByteArray();
        message.data(SdkBytes.fromByteBuffer(ByteBuffer.wrap(messageByteArray)));
        return message.build();
    }

    @SuppressWarnings("unchecked")
    private Collection<String> determineReplyToAddresses(Exchange exchange) {
        List<String> replyToAddresses = exchange.getIn().getHeader(Ses2Constants.REPLY_TO_ADDRESSES, List.class);
        if (replyToAddresses == null) {
            replyToAddresses = getConfiguration().getReplyToAddresses();
        }
        return replyToAddresses;
    }

    private String determineReturnPath(Exchange exchange) {
        String returnPath = exchange.getIn().getHeader(Ses2Constants.RETURN_PATH, String.class);
        if (returnPath == null) {
            returnPath = getConfiguration().getReturnPath();
        }
        return returnPath;
    }

    @SuppressWarnings("unchecked")
    private Destination determineTo(Exchange exchange) {
        List<String> to = exchange.getIn().getHeader(Ses2Constants.TO, List.class);
        if (to == null) {
            to = getConfiguration().getTo();
        }
        return Destination.builder().toAddresses(to).build();
    }

    @SuppressWarnings("unchecked")
    private List<String> determineRawTo(Exchange exchange) {
        List<String> to = exchange.getIn().getHeader(Ses2Constants.TO, List.class);
        if (to == null) {
            to = getConfiguration().getTo();
        }
        return to;
    }

    private String determineFrom(Exchange exchange) {
        String from = exchange.getIn().getHeader(Ses2Constants.FROM, String.class);
        if (from == null) {
            from = getConfiguration().getFrom();
        }
        return from;
    }

    private String determineSubject(Exchange exchange) {
        String subject = exchange.getIn().getHeader(Ses2Constants.SUBJECT, String.class);
        if (subject == null) {
            subject = getConfiguration().getSubject();
        }
        return subject;
    }

    protected Ses2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (sesProducerToString == null) {
            sesProducerToString = "SesProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return sesProducerToString;
    }

    @Override
    public Ses2Endpoint getEndpoint() {
        return (Ses2Endpoint) super.getEndpoint();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
