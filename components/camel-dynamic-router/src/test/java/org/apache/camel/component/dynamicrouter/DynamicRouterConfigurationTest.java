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
