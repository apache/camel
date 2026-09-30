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
package org.apache.camel.management;

import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledOnOs(OS.AIX)
public class ManagedErrorRegistryTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getErrorRegistry().setEnabled(true);
        return context;
    }

    @Test
    public void testBrowseErrorsOfSameExchange() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(2);
        template.sendBody("direct:start", "a,b,c");
        assertMockEndpointsSatisfied();

        // the failures of the two parts of the split are recorded under the same exchange id
        assertEquals(2, context.getErrorRegistry().browse().size());

        MBeanServer mbeanServer = getMBeanServer();
        Set<ObjectName> names = mbeanServer.queryNames(
                new ObjectName("org.apache.camel:context=" + context.getManagementName() + ",*"), null);
        ObjectName on = names.stream().filter(n -> n.getKeyProperty("name") != null
                && n.getKeyProperty("name").contains("ErrorRegistry")).findFirst().orElseThrow();

        TabularData data = (TabularData) mbeanServer.invoke(on, "browse", null, null);
        assertEquals(2, data.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start")
                        .split(body().tokenize(","))
                        .filter(body().isNotEqualTo("b"))
                        .throwException(IllegalArgumentException.class, "Forced ${body}");
            }
        };
    }
}
