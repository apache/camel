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
package org.apache.camel.opentracing;

import java.util.concurrent.TimeUnit;

import io.opentracing.mock.MockTracer;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringOpenTracingSimpleRouteTest extends CamelSpringTestSupport {

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/opentracing/OpenTracingSimpleRouteTest.xml");
    }

    @Test
    public void testRoute() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(5).create();

        for (int i = 0; i < 5; i++) {
            template.sendBody("seda:dude", "Hello World");
        }

        assertTrue(notify.matches(30, TimeUnit.SECONDS));

        MockTracer tracer = (MockTracer) context().getRegistry().lookupByName("mockTracer");

        // Four spans per invocation, one for client, two for dude route (server and client to), one for car route
        assertEquals(20, tracer.finishedSpans().size());
    }
}
