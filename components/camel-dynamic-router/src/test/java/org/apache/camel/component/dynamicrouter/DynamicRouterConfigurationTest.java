package org.apache.camel.component.dynamicrouter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DynamicRouterConfigurationTest {

    @Test
    void testParsePath() {
        String channel = "control";
        String action = "subscribe";
        String subscribeChannel = "test";
        String testPath = String.format("%s/%s/%s", channel, action, subscribeChannel);
        DynamicRouterConfiguration configuration = new DynamicRouterConfiguration();
        configuration.parsePath(testPath);

        assertEquals(channel, configuration.getChannel());
        assertEquals(action, configuration.getControlAction());
        assertEquals(subscribeChannel, configuration.getSubscribeChannel());
    }

    @Test
    void testParsePathWithUriSyntaxError() {
        String channel = "control";
        String action = "subscribe";
        String testPath = String.format("%s/%s", channel, action);
        DynamicRouterConfiguration configuration = new DynamicRouterConfiguration();

        assertThrows(IllegalArgumentException.class, () -> configuration.parsePath(testPath));
    }
}
