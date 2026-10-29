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
package org.apache.camel.component.stub;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.console.DevConsole;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browsing a stub endpoint with a limit gives the latest messages.
 */
public class StubConsoleLimitTest {

    private CamelContext context;

    @BeforeEach
    public void setUp() {
        context = new DefaultCamelContext();
        context.addComponent("stub", new StubComponent());
        context.start();
    }

    @AfterEach
    public void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    public void testLimitGivesLatestMessages() {
        ProducerTemplate template = context.createProducerTemplate();
        for (int i = 1; i <= 5; i++) {
            template.sendBody("stub:nobody", "Message " + i);
        }

        StubConsole console = new StubConsole();
        console.setCamelContext(context);
        Map<String, Object> options = Map.of(StubConsole.BROWSE, "true", StubConsole.LIMIT, "2");

        String text = (String) console.call(DevConsole.MediaType.TEXT, options);
        assertTrue(text.contains("Message 4"), text);
        assertTrue(text.contains("Message 5"), text);
        assertFalse(text.contains("Message 3"), text);

        String json = console.call(DevConsole.MediaType.JSON, options).toString();
        assertTrue(json.contains("Message 4"), json);
        assertTrue(json.contains("Message 5"), json);
        assertFalse(json.contains("Message 3"), json);
    }
}
