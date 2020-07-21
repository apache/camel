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
import org.junit.After;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConfigurationTest {

    private AbstractXmlApplicationContext context;

    @After
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
        assertEquals("ExpectReply should be false ", camelSourceA.isExpectReply(), false);
        CamelSourceAdapter camelSourceB = context.getBean("camelSourceB", CamelSourceAdapter.class);
        assertNotNull(camelSourceB);
        assertTrue(camelSourceB.getChannel().toString().contains("channelB"));
        assertEquals("ExpectReply should be true ", camelSourceB.isExpectReply(), true);
    }

    @Test
    public void testCamelTragetEndpoint() throws Exception {
        context = new ClassPathXmlApplicationContext(new String[]{"/org/apache/camel/component/spring/integration/adapter/CamelTarget.xml"});
        context.start();

        CamelTargetAdapter camelTargetA = context.getBean("camelTargetA", CamelTargetAdapter.class);

        assertNotNull(camelTargetA);
        assertEquals("Subscript the wrong CamelEndpointUri", camelTargetA.getCamelEndpointUri(), "direct:EndpointA");
        CamelTargetAdapter camelTargetB = context.getBean("camelTargetB", CamelTargetAdapter.class);
        assertNotNull(camelTargetB);
        assertTrue(camelTargetB.getReplyChannel().toString().contains("channelC"));
    }
}
