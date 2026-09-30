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

import java.io.StringReader;
import java.lang.reflect.Field;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import org.xml.sax.InputSource;

import org.apache.camel.CamelContext;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.management.mbean.LoadTriplet;
import org.apache.camel.management.mbean.ManagedPerformanceCounter;
import org.apache.camel.management.mbean.ManagedRoute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedRouteAndContextEdgeCasesTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().getManagementAgent().setStatisticsLevel(ManagementStatisticsLevel.Extended);
        return context;
    }

    private ManagedCamelContext mcc() {
        return context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
    }

    private static Document parse(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    @Test
    public void testDumpStepStatsAsXmlIsWellFormed() throws Exception {
        template.sendBody("direct:start", "Hello");
        template.sendBody("direct:other", "Hello");

        Document doc = parse(mcc().getManagedCamelContext().dumpStepStatsAsXml(true));
        // every route, with the steps of the route
        assertEquals(3, doc.getElementsByTagName("routeStat").getLength());
        assertEquals(3, doc.getElementsByTagName("stepStats").getLength());
        assertEquals(2, doc.getElementsByTagName("stepStat").getLength());
    }

    @Test
    public void testDumpWithoutGeneratedIds() throws Exception {
        ManagedRouteMBean route = mcc().getManagedRoute("route1");
        assertFalse(route.dumpRouteAsXml(false, false).contains("id=\"to"));
        assertTrue(route.dumpRouteAsXml(false, true).contains("id=\"to"));

        ManagedCamelContextMBean camel = mcc().getManagedCamelContext();
        assertFalse(camel.dumpRoutesAsXml(false, false).contains("id=\"to"));
        assertFalse(camel.dumpRoutesAsYaml(false, false, false).contains("id: to"));
    }

    @Test
    public void testResetIncludesSteps() throws Exception {
        template.sendBody("direct:start", "Hello");
        assertEquals(1, mcc().getManagedStep("foo").getExchangesTotal());

        mcc().getManagedRoute("route1").reset(true);
        assertEquals(0, mcc().getManagedStep("foo").getExchangesTotal());
    }

    @Test
    public void testResetOfRouteIdWithWildcard() throws Exception {
        template.sendBody("direct:star", "Hello");
        template.sendBody("direct:start", "Hello");

        mcc().getManagedRoute("rou*").reset(true);
        assertEquals(0, mcc().getManagedProcessor("starLog").getExchangesTotal());
        // route1 is not the route rou* (but matches it as a wildcard)
        assertEquals(1, mcc().getManagedProcessor("fooLog").getExchangesTotal());
    }

    @Test
    public void testPercentileWhenCountGoesPastTheWindow() throws Exception {
        ManagedRoute route = new ManagedRoute(context, context.getRoute("route1"));
        route.init(context.getManagementStrategy());
        route.completedExchange(template.send("direct:start", e -> e.getMessage().setBody("Hello")), 5);

        // the count is updated without a lock, so concurrent exchanges can take it one past the window
        Field count = ManagedPerformanceCounter.class.getDeclaredField("percentileCount");
        count.setAccessible(true);
        count.setInt(route, 1025);

        route.getProcessingTimeP50();
        parse(route.dumpStatsAsXml(true));
    }

    @Test
    public void testResetResetsLoad() throws Exception {
        ManagedRoute route = new ManagedRoute(context, context.getRoute("route1"));
        route.init(context.getManagementStrategy());
        Field field = ManagedRoute.class.getDeclaredField("load");
        field.setAccessible(true);
        LoadTriplet load = (LoadTriplet) field.get(route);
        load.update(5);
        assertFalse(route.getLoad01().isEmpty());

        route.reset();
        assertEquals("", route.getLoad01());
    }

    @Test
    public void testDumpRouteStatsWithQuoteInGroup() throws Exception {
        template.sendBody("direct:other", "Hello");
        parse(mcc().getManagedRoute("other").dumpRouteStatsAsXml(true, true));
        parse(mcc().getManagedCamelContext().dumpRoutesStatsAsXml(true, true));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("route1")
                        .step("foo")
                        .to("log:foo").id("fooLog")
                        .end()
                        .to("mock:result");

                from("direct:other").routeId("other").group("my\"group")
                        .step("bar")
                        .to("log:bar").id("otherLog")
                        .end();

                from("direct:star").routeId("rou*")
                        .to("log:star").id("starLog");
            }
        };
    }
}
