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
package org.apache.camel.component.spring.integration.adapter;

import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigurationTest {

    private AbstractXmlApplicationContext context;

    @AfterEach
    public void tearDown() {
        IOHelper.close(context);
    }

    @Test
    public void testCamelSourceEndpoint() throws Exception {
        context = new ClassPathXmlApplicationContext("/org/apache/camel/component/spring/integration/adapter/CamelSource.xml");
        context.start();

        CamelSourceAdapter camelSourceA = context.getBean("camelSourceA", CamelSourceAdapter.class);

        assertNotNull(camelSourceA);
        assertTrue(camelSourceA.getChannel().toString().contains("channelA"));
        assertFalse(camelSourceA.isExpectReply(), "ExpectReply should be false");
        CamelSourceAdapter camelSourceB = context.getBean("camelSourceB", CamelSourceAdapter.class);
        assertNotNull(camelSourceB);
        assertTrue(camelSourceB.getChannel().toString().contains("channelB"));
        assertTrue(camelSourceB.isExpectReply(), "ExpectReply should be true");
    }

    @Test
    public void testCamelTragetEndpoint() throws Exception {
        context = new ClassPathXmlApplicationContext(
                new String[] { "/org/apache/camel/component/spring/integration/adapter/CamelTarget.xml" });
        context.start();

        CamelTargetAdapter camelTargetA = context.getBean("camelTargetA", CamelTargetAdapter.class);

        assertNotNull(camelTargetA);
        assertEquals("direct:EndpointA", camelTargetA.getCamelEndpointUri(), "Subscript the wrong CamelEndpointUri");
        CamelTargetAdapter camelTargetB = context.getBean("camelTargetB", CamelTargetAdapter.class);
        assertNotNull(camelTargetB);
        assertTrue(camelTargetB.getReplyChannel().toString().contains("channelC"));
    }
}
