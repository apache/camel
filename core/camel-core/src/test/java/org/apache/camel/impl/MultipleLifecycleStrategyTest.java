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
package org.apache.camel.impl;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.TestSupport;
import org.apache.camel.component.log.LogComponent;
import org.junit.Test;

public class MultipleLifecycleStrategyTest extends TestSupport {

    private DummyLifecycleStrategy dummy1 = new DummyLifecycleStrategy();
    private DummyLifecycleStrategy dummy2 = new DummyLifecycleStrategy();

    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addLifecycleStrategy(dummy1);
        context.addLifecycleStrategy(dummy2);
        return context;
    }

    @Test
    public void testMultipleLifecycleStrategies() throws Exception {
        CamelContext context = createCamelContext();
        context.start();

        Component log = new LogComponent();
        context.addComponent("log", log);
        context.addEndpoint("log:/foo", log.createEndpoint("log://foo"));
        context.removeComponent("log");
        context.stop();

        List<String> expectedEvents = Arrays.asList(
            "onServiceAdd", "onServiceAdd",
            "onServiceAdd", "onServiceAdd", "onServiceAdd", "onServiceAdd", "onServiceAdd",
            "onServiceAdd", "onServiceAdd", "onServiceAdd", "onServiceAdd",
            "onContextStart",
            "onComponentAdd", "onEndpointAdd", "onComponentRemove", "onContextStop");
        
        assertEquals(expectedEvents, dummy1.getEvents());
        assertEquals(expectedEvents, dummy2.getEvents());
    }

}
