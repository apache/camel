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
package org.apache.camel.management;

import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.CastUtils;

/**
 *
 */
public class RecipientListProducerCacheLimitTest extends ManagementTestSupport {

    public void testRecipientListCacheLimit() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        for (int i = 0; i < 100; i++) {
            getMockEndpoint("mock:foo" + i).expectedMessageCount(1);
        }

        for (int i = 0; i < 100; i++) {
            template.sendBodyAndHeader("direct:start", "Hello World", "foo", "mock:foo" + i);
        }

        assertMockEndpointsSatisfied();

        // services
        Set<ObjectName> names = CastUtils.cast(mbeanServer.queryNames(new ObjectName("org.apache.camel" + ":type=services,*"), null));
        ObjectName on;
        int found = 0;
        for (ObjectName name : names) {
            if (name.toString().contains("ProducerCache")) {
                found++;
                on = name;

                Integer max = (Integer) mbeanServer.getAttribute(on, "MaximumCacheSize");
                assertEquals(40, max.intValue());

                Integer current = (Integer) mbeanServer.getAttribute(on, "Size");
                assertEquals(40, current.intValue());

                String source = (String) mbeanServer.getAttribute(on, "Source");
                assertEquals("RecipientList[header(foo)]", source);
            }
        }

        assertEquals("Should find 1 ProducerCache", 1, found);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getProperties().put(Exchange.MAXIMUM_CACHE_POOL_SIZE, "40");
        return context;
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .recipientList(header("foo"));
            }
        };
    }
}
