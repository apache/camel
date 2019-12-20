package org.apache.camel.tooling.util;

import org.junit.Test;

import static org.apache.camel.tooling.util.Strings.asTitle;
import static org.apache.camel.tooling.util.Strings.between;
import static org.junit.Assert.assertEquals;

public class StringsTest {

    @Test
    public void testBetween() {
        assertEquals("org.apache.camel.model.OnCompletionDefinition", between("java.util.List<org.apache.camel.model.OnCompletionDefinition>", "<", ">"));
    }

    @Test
    public void testAsTitle() {
        assertEquals("Broker URL", asTitle("brokerURL"));
        assertEquals("Expose All Queues", asTitle("exposeAllQueues"));
        assertEquals("Reply To Concurrent Consumers", asTitle("replyToConcurrentConsumers"));
    }

    @Test
    public void testWrap() {
        assertEquals("Hello WorldFoo Nar", wrap("HelloWorldFooNar", 8));
        assertEquals("UseMessageIDAs CorrelationID", wrap("useMessageIDAsCorrelationID", 25));
        assertEquals("ReplyToCacheLevelName", wrap("replyToCacheLevelName", 25));
        assertEquals("AllowReplyManagerQuick Stop", wrap("allowReplyManagerQuickStop", 25));
        assertEquals("AcknowledgementModeName", wrap("acknowledgementModeName", 25));
        assertEquals("ReplyToCacheLevelName", wrap("replyToCacheLevelName", 25));
        assertEquals("ReplyToOnTimeoutMax ConcurrentConsumers", wrap("replyToOnTimeoutMaxConcurrentConsumers", 25));
        assertEquals("ReplyToOnTimeoutMax ConcurrentConsumers", wrap("replyToOnTimeoutMaxConcurrentConsumers", 23));
        assertEquals("ReplyToMaxConcurrent Consumers", wrap("replyToMaxConcurrentConsumers", 23));

    }

    private String wrap(String str, int watermark) {
        return Strings.wrapCamelCaseWords(str, watermark, " ");
    }
}
