package org.apache.camel.component.salesforce.internal.streaming;

import org.apache.logging.log4j.core.util.ReflectionUtil;
import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.common.HashMapMessage;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static org.junit.Assert.assertEquals;

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

    @Test
    public void shouldKeepPreviousValueIfReplayIdNotInMessageWhenIsSupported() throws NoSuchFieldException {
        final Message.Mutable pushTopicMessage = createPushTopicMessage(false);

        final ReplayExtension replayExtension = new ReplayExtension();
        replayExtension.rcvMeta(null, createHandshakeMessage(true));

        replayExtension.addChannelReplayId(pushTopicMessage.getChannel(), 123L);

        replayExtension.rcv(null, pushTopicMessage);

        ConcurrentMap<String, Long> dataMap = (ConcurrentMap<String, Long>) ReflectionUtil.getFieldValue(
                ReplayExtension.class.getDeclaredField("dataMap"),
                replayExtension);

        assertEquals(Long.valueOf(123L), dataMap.get("/topic/AccountUpdates"));

    }

    @Test
    public void shouldUpdateReplayIdFromMessageWhenIsSupported() throws NoSuchFieldException {
        final Message.Mutable pushTopicMessage = createPushTopicMessage(true);

        final ReplayExtension replayExtension = new ReplayExtension();
        replayExtension.rcvMeta(null, createHandshakeMessage(true));

        replayExtension.addChannelReplayId(pushTopicMessage.getChannel(), 123L);

        replayExtension.rcv(null, pushTopicMessage);

        ConcurrentMap<String, Long> dataMap = (ConcurrentMap<String, Long>) ReflectionUtil.getFieldValue(
                ReplayExtension.class.getDeclaredField("dataMap"),
                replayExtension);

        assertEquals(Long.valueOf(1L), dataMap.get("/topic/AccountUpdates"));

    }

    @Test
    public void shouldNotUpdateReplayIdFromMessageWhenIsNotSupported() throws NoSuchFieldException {
        final Message.Mutable pushTopicMessage = createPushTopicMessage(true);

        final ReplayExtension replayExtension = new ReplayExtension();
        replayExtension.rcvMeta(null, createHandshakeMessage(false));

        replayExtension.rcv(null, pushTopicMessage);

        ConcurrentMap<String, Long> dataMap = (ConcurrentMap<String, Long>) ReflectionUtil.getFieldValue(
                ReplayExtension.class.getDeclaredField("dataMap"),
                replayExtension);

        assertEquals(0, dataMap.size());

    }
}
