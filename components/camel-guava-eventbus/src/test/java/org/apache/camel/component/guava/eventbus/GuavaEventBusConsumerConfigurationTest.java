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
package org.apache.camel.component.guava.eventbus;

import com.google.common.eventbus.EventBus;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class GuavaEventBusConsumerConfigurationTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void invalidConfiguration() throws Exception {
        // Given
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("eventBus", new EventBus());
        CamelContext context = new DefaultCamelContext(registry);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("guava-eventbus:eventBus?listenerInterface=org.apache.camel.component.guava.eventbus.CustomListener&eventClass=org.apache.camel.component.guava.eventbus.MessageWrapper")
                        .to("mock:customListenerEvents");
            }
        });

        try {
            context.start();
            fail("Should throw exception");
        } catch (Exception e) {
            IllegalStateException ise = assertIsInstanceOf(IllegalStateException.class, e.getCause());
            assertEquals("You cannot set both 'eventClass' and 'listenerInterface' parameters.", ise.getMessage());
        }
    }

}
