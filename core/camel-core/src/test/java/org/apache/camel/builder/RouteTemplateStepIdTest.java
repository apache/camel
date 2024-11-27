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

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.processor.StepProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RouteTemplateStepIdTest extends ContextTestSupport {

    @Test
    public void testStepId() throws Exception {
        context.addRouteFromTemplate("one", "myTemplate", Map.of("name", "one", "greeting", "Hello"));
        context.addRouteFromTemplate("deux", "myTemplate", Map.of("name", "deux", "greeting", "Bonjour", "myPeriod", "5s"));

        assertEquals(2, context.getRoutes().size());

        String nodePrefix = context.getRoute("one").getNodePrefixId();
        StepProcessor step1 = context.getProcessor(nodePrefix + "one", StepProcessor.class);
        Assertions.assertNotNull(step1);
        Assertions.assertEquals(nodePrefix + "one", step1.getId());
        Assertions.assertEquals("one", step1.getRouteId());

        nodePrefix = context.getRoute("deux").getNodePrefixId();
        StepProcessor step2 = context.getProcessor(nodePrefix + "deux", StepProcessor.class);
        Assertions.assertNotNull(step2);
        Assertions.assertEquals(nodePrefix + "deux", step2.getId());
        Assertions.assertEquals("deux", step2.getRouteId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("myTemplate").templateParameter("name").templateParameter("greeting")
                        .templateParameter("myPeriod", "3s")
                        .from("timer:{{name}}?period={{myPeriod}}")
                        .step("{{name}}")
                            .setBody(simple("{{greeting}} {{name}}"))
                            .log("${body}")
                        .end();
            }
        };
    }
}
