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
package org.apache.camel.component.kamelet;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.StringHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ManagedKameletRouteDisabledTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testKameletRouteMBeanDisabled() throws Exception {
        String body = UUID.randomUUID().toString();

        assertThat(
                fluentTemplate.toF("direct:single").withBody(body).request(String.class)).isEqualTo("a-" + body);

        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=routes,*"), null);
        assertEquals(1, set.size());

        Set<String> ids = new HashSet<>();
        for (ObjectName on : set) {
            String uri = (String) mbeanServer.getAttribute(on, "EndpointUri");
            String name = StringHelper.before(uri, ":");
            ids.add(name);
        }
        assertTrue(ids.contains("direct"));
        // is disabled by default
        assertFalse(ids.contains("kamelet"));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                routeTemplate("echo")
                        .templateParameter("prefix")
                        .from("kamelet:source")
                        .setBody().simple("{{prefix}}-${body}");

                from("direct:single").routeId("test")
                        .to("kamelet:echo?prefix=a")
                        .log("${body}");
            }
        };
    }
}
