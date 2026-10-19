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
package org.apache.camel.component.slack.dst;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.slack.api.RequestConfigurator;
import com.slack.api.Slack;
import com.slack.api.SlackConfig;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.conversations.ConversationsHistoryRequest;
import com.slack.api.methods.response.conversations.ConversationsHistoryResponse;
import com.slack.api.methods.response.conversations.ConversationsListResponse;
import com.slack.api.model.Conversation;
import com.slack.api.model.Message;
import com.slack.api.model.ResponseMetadata;
import com.slack.api.util.http.SlackHttpClient;
import org.apache.camel.Consumer;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.slack.helper.SlackHelper;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstCamel;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.camel.test.dst.DstLedger;
import org.apache.camel.test.dst.Invariants;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * The world around a camel-slack consumer: a fake Slack Web API (conversations.history answers as Slack does: the
 * messages after {@code oldest}, the most recent first, {@code limit} per page) whose calls are blocking calls with a
 * latency chosen by the seed, a channel that receives bursts of messages, a route whose step is a blocking downstream
 * call, and an operator that suspends and resumes the consumer at seeded times (so a suspend can land between two
 * exchanges of one batch). The consumer, its scheduled poll and Camel's routing are real.
 */
final class SlackWorld {

    static final String ROUTE = "slack";
    private static final SlackConfig CONFIG = new SlackConfig();

    static {
        CONFIG.setStatsEnabled(false);
    }

    private final Dst dst;
    private final DstLedger ledger;
    private final List<String> channel = new ArrayList<>();
    private final int maxResults;
    private final boolean naturalOrder;
    private final long delay;
    private final DstExecutor operator;
    private Consumer consumer;
    private DefaultCamelContext context;
    private int postsLeft;
    private int suspendsLeft;
    private boolean suspended;
    private boolean faults = true;
    private long lastResume;
    private long pollsAfterLastResume;

    SlackWorld(Dst dst) {
        this.dst = dst;
        this.ledger = new DstLedger(dst);
        this.maxResults = dst.pick(List.of(2, 3, 5, 10));
        this.naturalOrder = dst.chance(0.5);
        this.delay = dst.pick(List.of(200L, 500L, 1000L));
        this.operator = dst.executor("operator", 1);
        dst.trace("world: maxResults=" + maxResults + " naturalOrder=" + naturalOrder + " delay=" + delay);
    }

    void run() throws Exception {
        // the history before the consumer starts: the first poll reads the most recent message only
        channel.add("m0");
        context = DstCamel.newContext(dst);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("slack://general?token=RAW(xoxp-dst)&maxResults=" + maxResults + "&delay=" + delay
                     + "&initialDelay=100&naturalOrder=" + naturalOrder)
                        .routeId(ROUTE)
                        .process(exchange -> {
                            String text = exchange.getMessage().getBody(Message.class).getText();
                            ledger.record("routed", "general", text);
                            long latency = dst.between(1, 200);
                            if (faults && !suspended && suspendsLeft == 0 && dst.chance(0.1)) {
                                // fault at an interesting point (biased fault injection): the operator suspends the
                                // route while this exchange is in the downstream call
                                suspendAndResume(dst.between(0, latency), dst.between(1, 2 * delay));
                            }
                            // a blocking downstream call: other threads (the operator) run meanwhile
                            dst.block(latency, "downstream " + text);
                        });
            }
        });
        Slack slack = fakeSlack();
        try (MockedStatic<Slack> factory = Mockito.mockStatic(Slack.class);
             MockedStatic<SlackHelper> helper = Mockito.mockStatic(SlackHelper.class)) {
            // the Slack SDK starts metrics threads for every new SlackConfig (an escape point): share one without stats
            helper.when(() -> SlackHelper.createSlackConfig(any())).thenReturn(CONFIG);
            factory.when(() -> Slack.getInstance(any(SlackConfig.class), any(SlackHttpClient.class))).thenReturn(slack);
            context.start();
            consumer = context.getRoute(ROUTE).getConsumer();
            try {
                dst.runUntil(() -> !ledger.of("routed").isEmpty(), "the first poll");
                planPosts();
                planSuspends();
                dst.runUntil(() -> postsLeft == 0 && suspendsLeft == 0 && !suspended, "all posts and resumes");
                faults = false;
                // faults cleared: some more polls
                long since = dst.now();
                dst.runUntil(() -> pollsAfterLastResume >= 3 && dst.now() > since + 3 * delay, "three more polls");
                dst.check(
                        Invariants.everyOfferedRoutedAtLeastOnce(ledger, "posted", "routed"),
                        Invariants.noDuplicates(ledger, "routed"),
                        Invariants.routeStarted(context, ROUTE),
                        Invariants.of("consumer polls after the faults cleared",
                                () -> pollsAfterLastResume > 0 ? null : "no poll after the last resume"));
            } finally {
                dst.dropPending();
                context.stop();
            }
        }
    }

    private void planPosts() {
        DstExecutor users = dst.executor("slack-users", 1);
        long t = dst.between(0, delay);
        int n = 1;
        int bursts = (int) dst.between(1, 4);
        for (int b = 0; b < bursts; b++) {
            int size = (int) dst.between(1, 2L * maxResults + 2);
            for (int i = 0; i < size; i++) {
                String text = "m" + n++;
                postsLeft++;
                // a burst is posted at once, so that a poll sees all of it or none of it
                users.schedule("post " + text, t, () -> {
                    channel.add(text);
                    ledger.record("posted", "general", text);
                    postsLeft--;
                });
            }
            t += dst.between(delay, 4 * delay);
        }
    }

    private void planSuspends() {
        int cycles = (int) dst.between(0, 3);
        long t = 0;
        for (int i = 0; i < cycles; i++) {
            t += dst.between(0, 2 * delay);
            long duration = dst.between(1, 2 * delay);
            suspendAndResume(t, duration);
            t += duration;
        }
    }

    /**
     * The route suspend of DefaultShutdownStrategy as it reaches the consumer: deferShutdown(CompleteCurrentTaskOnly)
     * (the batch ends after the current exchange), suspendNow(consumer), wait for the in-flight exchanges,
     * prepareShutdown(consumer). Here the wait is a check every 10 virtual milliseconds instead of the strategy's
     * Thread.sleep(1000) (an escape point: real time).
     */
    private void suspendAndResume(long at, long duration) {
        suspendsLeft++;
        operator.schedule("suspend consumer", at, () -> {
            ((ShutdownAware) consumer).deferShutdown(ShutdownRunningTask.CompleteCurrentTaskOnly);
            ServiceHelper.suspendService(consumer);
            suspended = true;
            dst.trace("consumer suspended");
            awaitInflightThenPrepare();
        });
        operator.schedule("resume consumer", at + duration, () -> {
            ServiceHelper.resumeService(consumer);
            suspended = false;
            suspendsLeft--;
            lastResume = dst.now();
            pollsAfterLastResume = 0;
            dst.trace("consumer resumed");
        });
    }

    private void awaitInflightThenPrepare() {
        if (context.getInflightRepository().size(ROUTE) > 0) {
            operator.schedule("suspend: wait for in-flight exchanges", 10, this::awaitInflightThenPrepare);
        } else {
            ((ShutdownAware) consumer).prepareShutdown(true, false);
            dst.trace("consumer prepared for suspend (in-flight done)");
        }
    }

    private Slack fakeSlack() throws Exception {
        Slack slack = Mockito.mock(Slack.class);
        MethodsClient methods = Mockito.mock(MethodsClient.class);
        Mockito.when(slack.methods(anyString())).thenReturn(methods);
        Mockito.when(methods.conversationsList(any(RequestConfigurator.class))).thenAnswer(inv -> {
            ConversationsListResponse r = new ConversationsListResponse();
            r.setOk(true);
            Conversation c = new Conversation();
            c.setId("C1");
            c.setName("general");
            r.setChannels(List.of(c));
            ResponseMetadata md = new ResponseMetadata();
            md.setNextCursor("");
            r.setResponseMetadata(md);
            return r;
        });
        Mockito.when(methods.conversationsHistory(any(RequestConfigurator.class))).thenAnswer(inv -> {
            RequestConfigurator<ConversationsHistoryRequest.ConversationsHistoryRequestBuilder> cfg = inv.getArgument(0);
            ConversationsHistoryRequest req = cfg.configure(ConversationsHistoryRequest.builder()).build();
            return history(req);
        });
        return slack;
    }

    /** conversations.history: a blocking HTTP call; the answer reflects the channel when the call returns. */
    private ConversationsHistoryResponse history(ConversationsHistoryRequest req) {
        boolean firstPage = req.getCursor() == null;
        if (firstPage && dst.now() >= lastResume) {
            pollsAfterLastResume++;
        }
        long latency = dst.chance(0.1) ? dst.between(delay, 3 * delay) : dst.between(5, 100);
        dst.block(latency, "slack conversations.history oldest=" + req.getOldest() + " cursor=" + req.getCursor());
        ConversationsHistoryResponse r = new ConversationsHistoryResponse();
        // a failure of the first page fails the poll (retried by the next poll); a failure of a following page is
        // not injected: the consumer then routes the newest page and documents that older messages are skipped
        if (faults && firstPage && dst.chance(0.05)) {
            r.setOk(false);
            r.setError("ratelimited");
            dst.trace("slack answers ratelimited");
            return r;
        }
        // a cursor is stable as in Slack: it points below the oldest message of the previous page, so messages posted
        // meanwhile do not shift the following pages
        int below = req.getCursor() == null ? Integer.MAX_VALUE : Integer.parseInt(req.getCursor());
        List<Integer> range = new ArrayList<>();
        for (int i = 0; i < channel.size() && i < below; i++) {
            if (req.getOldest() == null || new BigDecimal(ts(i)).compareTo(new BigDecimal(req.getOldest())) > 0) {
                range.add(i);
            }
        }
        // the most recent first
        range.sort((a, b) -> Integer.compare(b, a));
        int limit = req.getLimit() != null ? req.getLimit() : 100;
        List<Message> page = new ArrayList<>();
        for (int i = 0; i < Math.min(range.size(), limit); i++) {
            Message m = new Message();
            m.setText(channel.get(range.get(i)));
            m.setTs(ts(range.get(i)));
            page.add(m);
        }
        boolean more = limit < range.size();
        r.setOk(true);
        r.setMessages(page);
        r.setHasMore(more);
        ResponseMetadata md = new ResponseMetadata();
        md.setNextCursor(more ? String.valueOf(range.get(limit - 1)) : "");
        r.setResponseMetadata(md);
        dst.trace("slack answers " + page.stream().map(Message::getText).toList() + (more ? " has_more" : ""));
        return r;
    }

    private static String ts(int index) {
        return (1700000000 + index) + ".000100";
    }
}
