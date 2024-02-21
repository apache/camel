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
package org.apache.camel.builder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.spi.HasId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RouteTemplateDuplicateIdIssueTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void shouldNotFailDueToDuplicatedNodeId() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("myTemplate")
                        .templateParameter("input")
                        .from("direct:{{input}}")
                        .recipientList(constant("mock:a,mock:b")).parallelProcessing()
                        .to("mock:result");
            }
        });

        Map one = new HashMap();
        one.put("input", "a");
        context.addRouteFromTemplate("testRouteId1", "myTemplate", one);

        Map two = new HashMap();
        two.put("input", "b");
        context.addRouteFromTemplate("testRouteId2", "myTemplate", two);

        assertDoesNotThrow(() -> context.start(), "Route creation should not fail");

        // should generate unique id per template for the runtime processors
        List<Processor> processors = getProcessors("recipientList*");
        assertEquals(2, processors.size());
        Processor p1 = processors.get(0);
        Processor p2 = processors.get(1);
        assertNotNull(p1);
        assertNotNull(p2);
        assertNotSame(p1, p2);
        assertNotEquals(((HasId) p1).getId(), ((HasId) p2).getId());

        context.stop();
    }

}
