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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.TestSupportNodeIdFactory;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ModelDumpLine;
import org.apache.camel.spi.ModelToStructureDumper;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledOnOs(OS.AIX)
public class DumpRouteStructureDoTryTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getCamelContextExtension().addContextPlugin(NodeIdFactory.class, new TestSupportNodeIdFactory());
        return context;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testDump() throws Exception {
        TestSupportNodeIdFactory.resetCounters();

        context.addRoutes(createRouteBuilder());
        context.start();

        ModelToStructureDumper dumper = PluginHelper.getModelToStructureDumper(context);
        List<ModelDumpLine> lines = dumper.dumpStructure(context, "myDoTryRoute", false);
        assertEquals(8, lines.size());
        assertEquals(0, lines.get(0).level());
        assertEquals("route", lines.get(0).type());
        assertEquals("myDoTryRoute", lines.get(0).id());
        assertEquals("route[myDoTryRoute]", lines.get(0).code());
        assertEquals(1, lines.get(1).level());
        assertEquals("from", lines.get(1).type());
        assertEquals("myDoTryRoute", lines.get(1).id());
        assertEquals("from[direct:start]", lines.get(1).code());
        assertEquals(2, lines.get(2).level());
        assertEquals("doTry", lines.get(2).type());
        assertEquals("doTry1", lines.get(2).id());
        assertEquals("doTry", lines.get(2).code());
        assertEquals(3, lines.get(3).level());
        assertEquals("split", lines.get(3).type());
        assertEquals("split1", lines.get(3).id());
        assertEquals("split[tokenize(body, ,)]", lines.get(3).code());
        assertEquals(4, lines.get(4).level());
        assertEquals("to", lines.get(4).type());
        assertEquals("to1", lines.get(4).id());
        assertEquals("to[mock:line]", lines.get(4).code());
        assertEquals(3, lines.get(5).level());
        assertEquals("doCatch", lines.get(5).type());
        assertEquals("doCatch1", lines.get(5).id());
        assertEquals("doCatch[java.lang.IllegalArgumentException]", lines.get(5).code());
        assertEquals(4, lines.get(6).level());
        assertEquals("to", lines.get(6).type());
        assertEquals("to2", lines.get(6).id());
        assertEquals("to[mock:iae]", lines.get(6).code());
        assertEquals(2, lines.get(7).level());
        assertEquals("to", lines.get(7).type());
        assertEquals("to3", lines.get(7).id());
        assertEquals("to[mock:end]", lines.get(7).code());
    }

    @Test
    public void testDumpBrief() throws Exception {
        TestSupportNodeIdFactory.resetCounters();

        context.addRoutes(createRouteBuilder());
        context.start();

        ModelToStructureDumper dumper = PluginHelper.getModelToStructureDumper(context);
        List<ModelDumpLine> lines = dumper.dumpStructure(context, "myDoTryRoute", true);
        assertEquals(8, lines.size());
        assertEquals(0, lines.get(0).level());
        assertEquals("route", lines.get(0).type());
        assertEquals("myDoTryRoute", lines.get(0).id());
        assertEquals("route[myDoTryRoute]", lines.get(0).code());
        assertEquals(1, lines.get(1).level());
        assertEquals("from", lines.get(1).type());
        assertEquals("myDoTryRoute", lines.get(1).id());
        assertEquals("from[direct:start]", lines.get(1).code());
        assertEquals(2, lines.get(2).level());
        assertEquals("doTry", lines.get(2).type());
        assertEquals("doTry1", lines.get(2).id());
        assertEquals("doTry", lines.get(2).code());
        assertEquals(3, lines.get(3).level());
        assertEquals("split", lines.get(3).type());
        assertEquals("split1", lines.get(3).id());
        assertEquals("split", lines.get(3).code());
        assertEquals(4, lines.get(4).level());
        assertEquals("to", lines.get(4).type());
        assertEquals("to1", lines.get(4).id());
        assertEquals("to[mock:line]", lines.get(4).code());
        assertEquals(3, lines.get(5).level());
        assertEquals("doCatch", lines.get(5).type());
        assertEquals("doCatch1", lines.get(5).id());
        assertEquals("doCatch", lines.get(5).code());
        assertEquals(4, lines.get(6).level());
        assertEquals("to", lines.get(6).type());
        assertEquals("to2", lines.get(6).id());
        assertEquals("to[mock:iae]", lines.get(6).code());
        assertEquals(2, lines.get(7).level());
        assertEquals("to", lines.get(7).type());
        assertEquals("to3", lines.get(7).id());
        assertEquals("to[mock:end]", lines.get(7).code());
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setDebugging(true);

                from("direct:start").routeId("myDoTryRoute")
                        .doTry()
                            .split(body().tokenize(","))
                                .to("mock:line")
                        .endDoTry()
                        .doCatch(IllegalArgumentException.class)
                            .to("mock:iae")
                        .end()
                        .to("mock:end");
            }
        };
    }

}
