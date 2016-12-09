package org.apache.camel.component.spring.integration;

import org.junit.Test;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.Assert.assertTrue;

public class SpringIntegrationMessageTest {

    @Test
    public void testCopyFrom() {
        org.springframework.messaging.Message testSpringMessage =
            MessageBuilder.withPayload("Test")
                .setHeader("header1", "value1")
                .setHeader("header2", "value2")
                .build();

        SpringIntegrationMessage original = new SpringIntegrationMessage(testSpringMessage);

        SpringIntegrationMessage copy = new SpringIntegrationMessage(testSpringMessage);

        copy.copyFrom(original);

        assertTrue(copy.getHeaders().containsKey("header1"));
        assertTrue(copy.getHeaders().containsKey("header2"));
    }

}
