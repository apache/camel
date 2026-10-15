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
package org.apache.camel.component.slack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.slack.api.Slack;
import com.slack.api.SlackConfig;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.conversations.ConversationsHistoryResponse;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.Message;
import com.slack.api.util.http.SlackHttpClient;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.slack.helper.SlackHelper;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlackConsumer extends ScheduledBatchPollingConsumer {

    public static final long DEFAULT_CONSUMER_DELAY = 10 * 1000L;

    private static final Logger LOG = LoggerFactory.getLogger(SlackConsumer.class);

    private static final int CONVERSATIONS_LIST_LIMIT = 200;
    private final SlackEndpoint slackEndpoint;
    private Slack slack;
    private String timestamp;
    // messages of a previous poll, the most recent first, that were not routed because the batch ended early. The
    // timestamp is already past them, so they are routed with the messages of the next poll
    private final List<Message> notRouted = new ArrayList<>();
    private String channelId;

    public SlackConsumer(SlackEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.slackEndpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        SlackConfig config = SlackHelper.createSlackConfig(slackEndpoint.getServerUrl());
        SlackHttpClient client = new SlackHttpClient();
        this.slack = Slack.getInstance(config, client);
        this.channelId = getChannelId(slackEndpoint.getChannel(), null);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (slack != null) {
            slack.close();
        }
    }

    @Override
    protected int poll() throws Exception {
        // Maximum limit is 1000. Slack recommends no more than 200 results at a time.
        // https://api.slack.com/methods/conversations.history
        // We set the limit to 1 the first call to set the timestamp of the last message of the history
        ConversationsHistoryResponse response = conversationsHistory(null);

        // okay we have some response from slack so lets mark the consumer as ready
        forceConsumerAsReady();

        List<Message> messages = response.getMessages();
        if (timestamp != null && response.isHasMore()) {
            // the most recent messages of the range come first, so the messages that did not fit into this response
            // are older ones: read the following pages, or they are skipped when the timestamp moves past them
            messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
            String cursor = nextCursor(response);
            while (cursor != null) {
                if (!isPollAllowed()) {
                    // stopping or suspended: do not keep calling Slack. The timestamp is not moved and nothing of this
                    // poll is routed, so the first poll after a restart or resume reads these messages again; routing
                    // the pages read so far would move the timestamp past the older messages that were not read
                    LOG.debug("Stopped reading the conversation history of channel {} as the consumer is stopping or"
                              + " suspended",
                            slackEndpoint.getChannel());
                    return 0;
                }
                try {
                    response = conversationsHistory(cursor);
                } catch (IOException | SlackApiException | RuntimeCamelException e) {
                    // for example rate limited: route the most recent messages already read, as when only one page was
                    // read, instead of failing every poll
                    LOG.warn("Cannot read the following page of the conversation history of channel {}: {}."
                             + " Routing the {} most recent new messages, the older messages of this poll are skipped.",
                            slackEndpoint.getChannel(), e.getMessage(), messages.size());
                    break;
                }
                if (response.getMessages() != null) {
                    messages.addAll(response.getMessages());
                }
                cursor = response.isHasMore() ? nextCursor(response) : null;
            }
        }

        if (ObjectHelper.isNotEmpty(messages)) {
            // the most recent message comes first
            timestamp = messages.get(0).getTs();
        }
        if (!notRouted.isEmpty()) {
            // they are older than the messages of this poll
            List<Message> all = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
            all.addAll(notRouted);
            notRouted.clear();
            messages = all;
        }

        Queue<Exchange> exchanges = createExchanges(messages);
        return processBatch(CastUtils.cast(exchanges));
    }

    private ConversationsHistoryResponse conversationsHistory(String cursor) throws IOException, SlackApiException {
        ConversationsHistoryResponse response = slack.methods(slackEndpoint.getToken()).conversationsHistory(req -> req
                .channel(channelId)
                .oldest(timestamp)
                .cursor(cursor)
                .limit(timestamp != null ? Integer.parseInt(slackEndpoint.getMaxResults()) : 1));

        if (!response.isOk()) {
            throw new RuntimeCamelException("API request conversations.history to Slack failed: " + response);
        }
        return response;
    }

    private static String nextCursor(ConversationsHistoryResponse response) {
        String cursor = response.getResponseMetadata() != null ? response.getResponseMetadata().getNextCursor() : null;
        return ObjectHelper.isNotEmpty(cursor) ? cursor : null;
    }

    private Queue<Exchange> createExchanges(final List<Message> list) {
        Queue<Exchange> answer = new LinkedList<>();
        if (ObjectHelper.isNotEmpty(list)) {
            if (slackEndpoint.isNaturalOrder()) {
                for (int i = list.size() - 1; i >= 0; i--) {
                    Message message = list.get(i);
                    Exchange exchange = createExchange(message);
                    answer.add(exchange);
                }
            } else {
                for (int i = 0; i < list.size(); i++) {
                    Message message = list.get(i);
                    Exchange exchange = createExchange(message);
                    answer.add(exchange);
                }
            }
        }
        return answer;
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();
        int answer = 0;

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            final Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // use default consumer callback
            AsyncCallback cb = defaultConsumerCallback(exchange, true);
            getAsyncProcessor().process(exchange, cb);
            answer++;
        }

        // the batch ended early as the consumer is suspending or stopping: the following polls do not read these
        // messages again, so keep them for the next poll. Their exchanges are never routed, and nothing else gives
        // them back to the exchange factory
        Exchange notRoutedExchange;
        while ((notRoutedExchange = (Exchange) exchanges.poll()) != null) {
            notRouted.add(notRoutedExchange.getIn().getBody(Message.class));
            releaseExchange(notRoutedExchange, true);
        }
        if (slackEndpoint.isNaturalOrder()) {
            // kept the most recent first
            Collections.reverse(notRouted);
        }

        return answer;
    }

    private String getChannelId(final String channel, final String cursor) {
        try {
            // Maximum limit is 1000. Slack recommends no more than 200 results at a time.
            // https://api.slack.com/methods/conversations.list
            ConversationsListResponse response = slack.methods(slackEndpoint.getToken()).conversationsList(req -> req
                    .types(Collections.singletonList(slackEndpoint.getConversationType()))
                    .cursor(cursor)
                    .limit(CONVERSATIONS_LIST_LIMIT));

            if (!response.isOk()) {
                throw new RuntimeCamelException("API request conversations.list to Slack failed: " + response);
            }

            return response.getChannels().stream()
                    .filter(it -> it.getName().equals(channel))
                    .map(Conversation::getId)
                    .findFirst().orElseGet(() -> {
                        if (ObjectHelper.isEmpty(response.getResponseMetadata().getNextCursor())) {
                            throw new RuntimeCamelException(String.format("Channel %s not found", channel));
                        }
                        return getChannelId(channel, response.getResponseMetadata().getNextCursor());
                    });
        } catch (IOException | SlackApiException e) {
            throw new RuntimeCamelException("API request conversations.list to Slack failed", e);
        }
    }

    private Exchange createExchange(Message object) {
        Exchange exchange = createExchange(true);
        exchange.getIn().setBody(object);
        return exchange;
    }
}
