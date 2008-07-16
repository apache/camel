/**
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

import junit.framework.TestCase;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.integration.endpoint.HandlerEndpoint;

public class ConfigurationTest extends TestCase {
    private AbstractXmlApplicationContext context;


    public void testCamelSourceEndpoint() throws Exception {
        context =
            new ClassPathXmlApplicationContext(new String[] {"/org/apache/camel/component/spring/integration/adapter/CamelSource.xml"});
        CamelSourceAdapter camelSourceA = (CamelSourceAdapter) context.getBean("camelSourceA");
        assertNotNull(camelSourceA);
        assertEquals("Get the wrong request channel name", camelSourceA.getChannel().getName(), "channelA");
        assertEquals("ExpectReply should be false ", camelSourceA.isExpectReply(), false);
        CamelSourceAdapter camelSourceB = (CamelSourceAdapter) context.getBean("camelSourceB");
        assertNotNull(camelSourceB);
        assertEquals("Get the wrong request channel name", camelSourceB.getChannel().getName(), "channelB");
        assertEquals("ExpectReply should be true ", camelSourceB.isExpectReply(), true);
        context.destroy();

    }

    public void testCamelTragetEndpoint() throws Exception {
        context =
            new ClassPathXmlApplicationContext(new String[] {"/org/apache/camel/component/spring/integration/adapter/CamelTarget.xml"});
        HandlerEndpoint handlerEndpointA = (HandlerEndpoint)context.getBean("camelTargetA");
        assertNotNull(handlerEndpointA);
        assertEquals("Subscript the wrong channel name", handlerEndpointA.getInputChannelName(), "channelA");
        HandlerEndpoint handlerEndpointB = (HandlerEndpoint)context.getBean("camelTargetA");
        assertNotNull(handlerEndpointB);
        assertEquals("Subscript the wrong channel name", handlerEndpointB.getInputChannelName(), "channelA");
        context.destroy();
    }
}
