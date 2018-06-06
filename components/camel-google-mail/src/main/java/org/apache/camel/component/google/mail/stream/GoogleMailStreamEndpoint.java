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
package org.apache.camel.component.google.mail.stream;

import java.io.UnsupportedEncodingException;
import java.util.List;

import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.MessagePartHeader;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.google.mail.GoogleMailClientFactory;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * The google-mail component provides access to Google Mail.
 */
@UriEndpoint(firstVersion = "2.22.0", 
             scheme = "google-mail-stream", 
             title = "Google Mail Stream", 
             syntax = "google-mail-stream:index", 
             consumerClass = GoogleMailStreamConsumer.class,
             consumerOnly = true,
             label = "api,cloud,mail")
public class GoogleMailStreamEndpoint extends ScheduledPollEndpoint {

    @UriParam
    private GoogleMailStreamConfiguration configuration;

    public GoogleMailStreamEndpoint(String uri, GoogleMailStreamComponent component, GoogleMailStreamConfiguration endpointConfiguration) {
        super(uri, component);
        this.configuration = endpointConfiguration;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new IllegalArgumentException("The camel google mail stream component doesn't support producer");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        String unreadLabelId = null;
        String readLabelId = null;
        ListLabelsResponse listResponse = getClient().users().labels().list("me").execute();
        for (Label label : listResponse.getLabels()) {
            Label countLabel = getClient().users().labels().get("me", label.getId()).execute();
            if (countLabel.getName().equalsIgnoreCase("UNREAD")) {
                unreadLabelId = countLabel.getId();
            }
        }
        final GoogleMailStreamConsumer consumer = new GoogleMailStreamConsumer(this, processor, unreadLabelId);
        configureConsumer(consumer);
        return consumer;
    }

    public Gmail getClient() {
        return ((GoogleMailStreamComponent)getComponent()).getClient(configuration);
    }

    public GoogleMailClientFactory getClientFactory() {
        return ((GoogleMailStreamComponent)getComponent()).getClientFactory();
    }

    public void setClientFactory(GoogleMailClientFactory clientFactory) {
        ((GoogleMailStreamComponent)getComponent()).setClientFactory(clientFactory);
    }

    public GoogleMailStreamConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public Exchange createExchange(ExchangePattern pattern, com.google.api.services.gmail.model.Message mail) throws UnsupportedEncodingException {

        Exchange exchange = super.createExchange();
        Message message = exchange.getIn();
        exchange.getIn().setHeader(GoogleMailStreamConstants.MAIL_ID, mail.getId());
        if (mail.getPayload().getParts() != null) {
            if (mail.getPayload().getParts().get(0).getBody().getData() != null) {
                byte[] bodyBytes = Base64.decodeBase64(mail.getPayload().getParts().get(0).getBody().getData().trim().toString()); // get
                                                                                                                                   // body
                String body = new String(bodyBytes, "UTF-8");
                message.setBody(body);
            }
        }
        setHeaders(message, mail.getPayload().getHeaders());
        return exchange;
    }

    private void setHeaders(Message message, List<MessagePartHeader> headers) {
        for (MessagePartHeader header : headers) {
            if (header.getName().equalsIgnoreCase("SUBJECT") || header.getName().equalsIgnoreCase("subject")) {
                message.setHeader(GoogleMailStreamConstants.MAIL_SUBJECT, header.getValue());
            }
            if (header.getName().equalsIgnoreCase("TO") || header.getName().equalsIgnoreCase("to")) {
                message.setHeader(GoogleMailStreamConstants.MAIL_TO, header.getValue());
            }
            if (header.getName().equalsIgnoreCase("FROM") || header.getName().equalsIgnoreCase("from")) {
                message.setHeader(GoogleMailStreamConstants.MAIL_FROM, header.getValue());
            }
            if (header.getName().equalsIgnoreCase("CC") || header.getName().equalsIgnoreCase("cc")) {
                message.setHeader(GoogleMailStreamConstants.MAIL_CC, header.getValue());
            }
            if (header.getName().equalsIgnoreCase("BCC") || header.getName().equalsIgnoreCase("bcc")) {
                message.setHeader(GoogleMailStreamConstants.MAIL_BCC, header.getValue());
            }
        }
    }
}
