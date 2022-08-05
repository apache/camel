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
package org.apache.camel.zipkin;

import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import zipkin2.reporter.Reporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedZipkinSimpleRouteTest extends CamelTestSupport {

    private ZipkinTracer zipkin;

    protected void setSpanReporter(ZipkinTracer zipkin) {
        zipkin.setSpanReporter(Reporter.NOOP);
    }

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        zipkin = new ZipkinTracer();
        zipkin.setServiceName("dude");
        setSpanReporter(zipkin);

        // attaching ourself to CamelContext
        zipkin.init(context);

        return context;
    }

    @Test
    public void testZipkinRoute() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = new ObjectName(
                "org.apache.camel:context=" + context.getManagementName() + ",type=services,name=ZipkinTracer");
        assertNotNull(on);
        assertTrue(mbeanServer.isRegistered(on));

        Float rate = (Float) mbeanServer.getAttribute(on, "Rate");
        assertEquals(1.0f, rate.floatValue(), 0.1f, "Should be 1.0f");

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(5).create();

        for (int i = 0; i < 5; i++) {
            template.sendBody("seda:dude", "Hello World");
        }

        assertTrue(notify.matches(30, TimeUnit.SECONDS));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:dude").routeId("dude")
                        .log("routing at ${routeId}")
                        .delay(simple("${random(10,20)}"));
            }
        };
    }
}
