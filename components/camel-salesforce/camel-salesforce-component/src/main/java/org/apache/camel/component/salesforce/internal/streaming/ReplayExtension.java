/*
 * Copyright (c) 2016, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.TXT file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package org.apache.camel.component.salesforce.internal.streaming;


import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSession;
import org.cometd.bayeux.client.ClientSession.Extension.Adapter;

/**
 * The Bayeux extension for replay
 *
 * @author hal.hildebrand
 * @since API v37.0
 */
public class ReplayExtension extends Adapter {
    private static final String EXTENSION_NAME = "replay";
    private static final String EVENT_KEY = "event";
    private static final String REPLAY_ID_KEY = "replayId";

    private final ConcurrentMap<String, Long> dataMap = new ConcurrentHashMap<>();
    private final AtomicBoolean supported = new AtomicBoolean();

    public void addChannelReplayId(final String channelName, final long replayId) {
        dataMap.put(channelName, replayId);
    }

    @Override
    public boolean rcv(ClientSession session, Message.Mutable message) {
        Long replayId = getReplayId(message);
        if (this.supported.get() && replayId != null) {
            try {
                String channel = topicWithoutQueryString(message.getChannel());
                dataMap.put(channel, replayId);
            } catch (ClassCastException e) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean rcvMeta(ClientSession session, Message.Mutable message) {
        switch (message.getChannel()) {
        case Channel.META_HANDSHAKE:
            Map<String, Object> ext = message.getExt(false);
            this.supported.set(ext != null && Boolean.TRUE.equals(ext.get(EXTENSION_NAME)));
        }
        return true;
    }

    @Override
    public boolean sendMeta(ClientSession session, Message.Mutable message) {
        switch (message.getChannel()) {
        case Channel.META_HANDSHAKE:
            message.getExt(true).put(EXTENSION_NAME, Boolean.TRUE);
            break;
        case Channel.META_SUBSCRIBE:
            if (supported.get()) {
                message.getExt(true).put(EXTENSION_NAME, dataMap);
            }
            break;
        }
        return true;
    }

    private static Long getReplayId(Message.Mutable message) {
        Map<String, Object> data = message.getDataAsMap();
        @SuppressWarnings("unchecked")
        Optional<Long> optional = resolve(() -> (Long)((Map<String, Object>)data.get(EVENT_KEY)).get(REPLAY_ID_KEY));
        return optional.orElse(null);
    }

    private static <T> Optional<T> resolve(Supplier<T> resolver) {
        try {
            T result = resolver.get();
            return Optional.ofNullable(result);
        }
        catch (NullPointerException e) {
            return Optional.empty();
        }
    }

    private static String topicWithoutQueryString(String fullTopic) {
        return fullTopic.split("\\?")[0];
    }
}