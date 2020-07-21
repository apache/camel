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
package org.apache.camel.component.salesforce.internal.streaming;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.common.HashMapMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReplayExtensionTest {

    static Message.Mutable createPushTopicMessage(boolean addReplayId) {
        final Message.Mutable pushTopicMessage = new HashMapMessage();
        pushTopicMessage.put("clientId", "lxdl9o32njygi1gj47kgfaga4k");

        final Map<String, Object> data = new HashMap<>();
        pushTopicMessage.put("data", data);

        final Map<String, Object> event = new HashMap<>();
        data.put("event", event);

        event.put("createdDate", "2016-09-16T19:45:27.454Z");
        if (addReplayId) {
            event.put("replayId", 1L);
        }
        event.put("type", "created");

        final Map<String, Object> sobject = new HashMap<>();
        data.put("sobject", sobject);

        sobject.put("Phone", "(415) 555-1212");
        sobject.put("Id", "001D000000KneakIAB");
        sobject.put("Name", "Blackbeard");

        pushTopicMessage.put("channel", "/topic/AccountUpdates");
        return pushTopicMessage;
    }

    static Message.Mutable createHandshakeMessage(Boolean isReplaySupported) {
        final Message.Mutable handshakeMessage = new HashMapMessage();
        HashMap<String, Object> ext = new HashMap<>();
        handshakeMessage.put("ext", ext);
        handshakeMessage.put("channel", Channel.META_HANDSHAKE);
        ext.put("replay", isReplaySupported);

        return handshakeMessage;
    }

    @SuppressWarnings("unchecked")
    static ConcurrentMap<String, Long> getDataMap(ReplayExtension replayExtension)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = ReplayExtension.class.getDeclaredField("dataMap");
        field.setAccessible(true);

        return (ConcurrentMap<String, Long>) field.get(replayExtension);
    }

    @Test
    public void shouldKeepPreviousValueIfReplayIdNotInMessageWhenIsSupported()
            throws NoSuchFieldException, IllegalAccessException {
        final Message.Mutable pushTopicMessage = createPushTopicMessage(false);

        final ReplayExtension replayExtension = new ReplayExtension();
        replayExtension.rcvMeta(null, createHandshakeMessage(true));

        replayExtension.addChannelReplayId(pushTopicMessage.getChannel(), 123L);

        replayExtension.rcv(null, pushTopicMessage);

        ConcurrentMap<String, Long> dataMap = getDataMap(replayExtension);

        assertEquals(Long.valueOf(123L), dataMap.get("/topic/AccountUpdates"));
    }

    @Test
    public void shouldUpdateReplayIdFromMessageWhenIsSupported() throws NoSuchFieldException, IllegalAccessException {
        final Message.Mutable pushTopicMessage = createPushTopicMessage(true);

        final ReplayExtension replayExtension = new ReplayExtension();
        replayExtension.rcvMeta(null, createHandshakeMessage(true));

        replayExtension.addChannelReplayId(pushTopicMessage.getChannel(), 123L);

        replayExtension.rcv(null, pushTopicMessage);

        ConcurrentMap<String, Long> dataMap = getDataMap(replayExtension);

        assertEquals(Long.valueOf(1L), dataMap.get("/topic/AccountUpdates"));

    }

    @Test
    public void shouldNotUpdateReplayIdFromMessageWhenIsNotSupported()
            throws NoSuchFieldException, IllegalAccessException {
        final Message.Mutable pushTopicMessage = createPushTopicMessage(true);

        final ReplayExtension replayExtension = new ReplayExtension();
        replayExtension.rcvMeta(null, createHandshakeMessage(false));

        replayExtension.rcv(null, pushTopicMessage);

        ConcurrentMap<String, Long> dataMap = getDataMap(replayExtension);

        assertEquals(0, dataMap.size());
    }
}
