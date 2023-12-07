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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.EmptyAsyncCallback;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GoogleMail consumer.
 */
public class GoogleMailStreamConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleMailStreamConsumer.class);
    private String unreadLabelId;
    private List<String> labelsIds;

    public GoogleMailStreamConsumer(Endpoint endpoint, Processor processor, String unreadLabelId, List<String> labelsIds) {
        super(endpoint, processor);
        this.unreadLabelId = unreadLabelId;
        this.labelsIds = labelsIds;
    }

    protected GoogleMailStreamConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    protected Gmail getClient() {
        return getEndpoint().getClient();
    }

    @Override
    public GoogleMailStreamEndpoint getEndpoint() {
        return (GoogleMailStreamEndpoint) super.getEndpoint();
    }

    @Override
    protected int poll() throws Exception {
        com.google.api.services.gmail.Gmail.Users.Messages.List request = getClient().users().messages().list("me");
        if (ObjectHelper.isNotEmpty(getConfiguration().getQuery())) {
            request.setQ(getConfiguration().getQuery());
        }
        if (ObjectHelper.isNotEmpty(getConfiguration().getMaxResults())) {
            request.setMaxResults(getConfiguration().getMaxResults());
        }
        if (ObjectHelper.isNotEmpty(labelsIds)) {
            request.setLabelIds(labelsIds);
        }

        Queue<Exchange> answer = new LinkedList<>();

        ListMessagesResponse c = request.execute();

        // okay we have some response from Google so lets mark the consumer as ready
        forceConsumerAsReady();

        if (c.getMessages() != null) {
            for (Message message : c.getMessages()) {
                Message mess = getClient().users().messages().get("me", message.getId()).setFormat("FULL").execute();
                Exchange exchange = createExchange(getEndpoint().getExchangePattern(), mess);
                answer.add(exchange);
            }
        }

        return processBatch(CastUtils.cast(answer));
    }

    @Override
    public int processBatch(Queue<Object> exchanges) {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // add on completion to handle after work when the exchange is done
            exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
                public void onComplete(Exchange exchange) {
                    processCommit(exchange, unreadLabelId);
                }

                public void onFailure(Exchange exchange) {
                    processRollback(exchange, unreadLabelId);
                }

                @Override
                public String toString() {
                    return "GoogleMailStreamConsumerOnCompletion";
                }
            });

            getAsyncProcessor().process(exchange, EmptyAsyncCallback.get());
        }

        return total;
    }

    /**
     * Strategy to delete the message after being processed.
     */
    protected void processCommit(Exchange exchange, String unreadLabelId) {
        try {
            if (getConfiguration().isMarkAsRead()) {
                String id = exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_ID, String.class);

                LOG.trace("Marking email {} as read", id);

                List<String> remove = new ArrayList<>();
                remove.add(unreadLabelId);
                ModifyMessageRequest mods = new ModifyMessageRequest().setRemoveLabelIds(remove);
                getClient().users().messages()
                        .modify("me", exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_ID, String.class), mods)
                        .execute();

                LOG.trace("Marked email {} as read", id);
            }
        } catch (Exception e) {
            getExceptionHandler().handleException("Error occurred mark as read mail. This exception is ignored.", exchange, e);
        }

    }

    /**
     * Strategy when processing the exchange failed.
     */
    protected void processRollback(Exchange exchange, String unreadLabelId) {
        try {
            LOG.warn("Exchange failed, so rolling back mail {} to un {}", exchange, unreadLabelId);

            List<String> add = new ArrayList<>();
            add.add(unreadLabelId);
            ModifyMessageRequest mods = new ModifyMessageRequest().setAddLabelIds(add);
            getClient().users().messages()
                    .modify("me", exchange.getIn().getHeader(GoogleMailStreamConstants.MAIL_ID, String.class), mods).execute();
        } catch (Exception e) {
            getExceptionHandler().handleException("Error occurred mark as read mail. This exception is ignored.", exchange, e);
        }
    }

    public Exchange createExchange(ExchangePattern pattern, com.google.api.services.gmail.model.Message mail) {
        Exchange exchange = createExchange(true);
        exchange.setPattern(pattern);
        org.apache.camel.Message message = exchange.getIn();
        exchange.getIn().setHeader(GoogleMailStreamConstants.MAIL_ID, mail.getId());
        if (getConfiguration().isRaw()) {
            message.setBody(mail.getRaw());
        } else {
            List<MessagePart> parts = mail.getPayload().getParts();
            if (parts != null && parts.get(0).getBody().getData() != null) {
                byte[] bodyBytes = Base64.decodeBase64(parts.get(0).getBody().getData().trim());
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                message.setBody(body);
            }
        }
        configureHeaders(message, mail.getPayload().getHeaders());
        return exchange;
    }

    private void configureHeaders(org.apache.camel.Message message, List<MessagePartHeader> headers) {
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

}
