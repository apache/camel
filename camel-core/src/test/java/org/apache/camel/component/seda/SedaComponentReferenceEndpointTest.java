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
package org.apache.camel.component.seda;

import java.util.Iterator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class SedaComponentReferenceEndpointTest extends ContextTestSupport {
    
    public void testSedaComponentReference() throws Exception {
        SedaComponent seda = context.getComponent("seda", SedaComponent.class);

        String key = seda.getQueueKey("seda://foo");
        assertEquals(1, seda.getQueues().get(key).getCount());
        assertEquals(2, numberOfReferences(seda));

        // add a second consumer on the endpoint
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo?blockWhenFull=true").routeId("foo2").to("mock:foo2");
            }
        });

        assertEquals(2, seda.getQueues().get(key).getCount());
        assertEquals(3, numberOfReferences(seda));

        // remove the 1st route
        context.stopRoute("foo");
        context.removeRoute("foo");

        assertEquals(1, seda.getQueues().get(key).getCount());
        assertEquals(2, numberOfReferences(seda));

        // remove the 2nd route
        context.stopRoute("foo2");
        context.removeRoute("foo2");

        // and there is no longer queues for the foo key
        assertNull(seda.getQueues().get(key));

        // there should still be a bar
        assertEquals(1, numberOfReferences(seda));
        key = seda.getQueueKey("seda://bar");
        assertEquals(1, seda.getQueues().get(key).getCount());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:foo").routeId("foo").to("mock:foo");

                from("seda:bar").routeId("bar").to("mock:bar");
            }
        };
    }
    
    private int numberOfReferences(SedaComponent seda) {
        int num = 0;
        Iterator<QueueReference> it = seda.getQueues().values().iterator();
        while (it.hasNext()) {
            num += it.next().getCount();
        }
        return num;
    }

}
