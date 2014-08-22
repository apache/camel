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
package org.apache.camel.itest.osgi.core.ref;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.itest.osgi.OSGiIntegrationTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;

@RunWith(PaxExam.class)
public class RefFileEndpointTest extends OSGiIntegrationTestSupport {

    private JndiRegistry jndi;

    @Test
    public void testRefFileEndpoint() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBodyAndHeader("file:target/foo", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();

        FileConsumer consumer = (FileConsumer) context.getRoute("foo").getConsumer();
        assertEquals(3000, consumer.getDelay());
        assertEquals(250, consumer.getInitialDelay());
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        jndi = super.createRegistry();
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                deleteDirectory("target/foo");
                jndi.bind("foo", context.getEndpoint("file:target/foo?initialDelay=250&delay=3000&delete=true"));

                from("ref:foo").routeId("foo").to("mock:result");
            }
        };
    }

}
