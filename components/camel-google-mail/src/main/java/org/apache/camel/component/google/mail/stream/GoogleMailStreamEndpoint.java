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
package org.apache.camel.component.google.mail.stream;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.ListLabelsResponse;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.common.base.Splitter;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.google.mail.GoogleMailClientFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * The google-mail component provides access to Google Mail.
 */
@UriEndpoint(firstVersion = "2.22.0",
             scheme = "google-mail-stream",
             title = "Google Mail Stream",
             syntax = "google-mail-stream:index",
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
        throw new UnsupportedOperationException("The camel google mail stream component doesn't support producer");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        String unreadLabelId = null;
        List<String> labelsIds = new ArrayList<>();
        ListLabelsResponse listResponse = getClient().users().labels().list("me").execute();
        for (Label label : listResponse.getLabels()) {
            Label countLabel = getClient().users().labels().get("me", label.getId()).execute();
            if (countLabel.getName().equalsIgnoreCase("UNREAD")) {
                unreadLabelId = countLabel.getId();
            }
        }
        if (ObjectHelper.isNotEmpty(getConfiguration().getLabels())) {
            List<String> plainLabels = splitLabels(getConfiguration().getLabels());
            for (Label label : listResponse.getLabels()) {
                Label countLabel = getClient().users().labels().get("me", label.getId()).execute();
                for (String plainLabel : plainLabels) {
                    if (countLabel.getName().equalsIgnoreCase(plainLabel)) {
                        labelsIds.add(countLabel.getId());
                    }
                }
            }
        }
        final GoogleMailStreamConsumer consumer = new GoogleMailStreamConsumer(this, processor, unreadLabelId, labelsIds);
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

    public Exchange createExchange(ExchangePattern pattern, com.google.api.services.gmail.model.Message mail) {

        Exchange exchange = super.createExchange(pattern);
        Message message = exchange.getIn();
        exchange.getIn().setHeader(GoogleMailStreamConstants.MAIL_ID, mail.getId());
        List<MessagePart> parts = mail.getPayload().getParts();
        if (parts != null && parts.get(0).getBody().getData() != null) {
            byte[] bodyBytes = Base64.decodeBase64(parts.get(0).getBody().getData().trim());
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            message.setBody(body);
        }
        setHeaders(message, mail.getPayload().getHeaders());
        return exchange;
    }

    private void setHeaders(Message message, List<MessagePartHeader> headers) {
        for (MessagePartHeader header : headers) {
            String headerName = header.getName();
            if ("SUBJECT".equalsIgnoreCase(headerName)) {
                message.setHeader(GoogleMailStreamConstants.MAIL_SUBJECT, header.getValue());
            }
            if ("TO".equalsIgnoreCase(headerName)) {
                message.setHeader(GoogleMailStreamConstants.MAIL_TO, header.getValue());
            }
            if ("FROM".equalsIgnoreCase(headerName)) {
                message.setHeader(GoogleMailStreamConstants.MAIL_FROM, header.getValue());
            }
            if ("CC".equalsIgnoreCase(headerName)) {
                message.setHeader(GoogleMailStreamConstants.MAIL_CC, header.getValue());
            }
            if ("BCC".equalsIgnoreCase(headerName)) {
                message.setHeader(GoogleMailStreamConstants.MAIL_BCC, header.getValue());
            }
        }
    }

    private List<String> splitLabels(String labels) {
        return Splitter.on(',').splitToList(labels);
    }
}
