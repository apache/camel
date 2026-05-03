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
public class DumpRouteStructureChoiceTest extends ManagementTestSupport {

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
        List<ModelDumpLine> lines = dumper.dumpStructure(context, "myChoiceRoute", false);
        assertEquals(10, lines.size());
        assertEquals(0, lines.get(0).level());
        assertEquals("route", lines.get(0).type());
        assertEquals("myChoiceRoute", lines.get(0).id());
        assertEquals("route[myChoiceRoute]", lines.get(0).code());
        assertEquals(1, lines.get(1).level());
        assertEquals("from", lines.get(1).type());
        assertEquals("myChoiceRoute", lines.get(1).id());
        assertEquals("from[direct:start]", lines.get(1).code());
        assertEquals(2, lines.get(2).level());
        assertEquals("choice", lines.get(2).type());
        assertEquals("choice1", lines.get(2).id());
        assertEquals("choice", lines.get(2).code());
        assertEquals(3, lines.get(3).level());
        assertEquals("when", lines.get(3).type());
        assertEquals("when1", lines.get(3).id());
        assertEquals("when[xpath{$foo = 'bar'}]", lines.get(3).code());
        assertEquals(4, lines.get(4).level());
        assertEquals("to", lines.get(4).type());
        assertEquals("to1", lines.get(4).id());
        assertEquals("to[mock:x]", lines.get(4).code());
        assertEquals(3, lines.get(5).level());
        assertEquals("when", lines.get(5).type());
        assertEquals("when2", lines.get(5).id());
        assertEquals("when[xpath{$foo = 'cheese'}]", lines.get(5).code());
        assertEquals(4, lines.get(6).level());
        assertEquals("to", lines.get(6).type());
        assertEquals("to2", lines.get(6).id());
        assertEquals("to[mock:y]", lines.get(6).code());
        assertEquals(3, lines.get(7).level());
        assertEquals("otherwise", lines.get(7).type());
        assertEquals("otherwise1", lines.get(7).id());
        assertEquals("otherwise", lines.get(7).code());
        assertEquals(4, lines.get(8).level());
        assertEquals("to", lines.get(8).type());
        assertEquals("to3", lines.get(8).id());
        assertEquals("to[mock:z]", lines.get(8).code());
        assertEquals(2, lines.get(9).level());
        assertEquals("to", lines.get(9).type());
        assertEquals("to4", lines.get(9).id());
        assertEquals("to[mock:end]", lines.get(9).code());
    }

    @Test
    public void testDumpBrief() throws Exception {
        TestSupportNodeIdFactory.resetCounters();

        context.addRoutes(createRouteBuilder());
        context.start();

        ModelToStructureDumper dumper = PluginHelper.getModelToStructureDumper(context);
        List<ModelDumpLine> lines = dumper.dumpStructure(context, "myChoiceRoute", true);
        assertEquals(10, lines.size());
        assertEquals(0, lines.get(0).level());
        assertEquals("route", lines.get(0).type());
        assertEquals("myChoiceRoute", lines.get(0).id());
        assertEquals("route[myChoiceRoute]", lines.get(0).code());
        assertEquals(1, lines.get(1).level());
        assertEquals("from", lines.get(1).type());
        assertEquals("myChoiceRoute", lines.get(1).id());
        assertEquals("from[direct:start]", lines.get(1).code());
        assertEquals(2, lines.get(2).level());
        assertEquals("choice", lines.get(2).type());
        assertEquals("choice1", lines.get(2).id());
        assertEquals("choice", lines.get(2).code());
        assertEquals(3, lines.get(3).level());
        assertEquals("when", lines.get(3).type());
        assertEquals("when1", lines.get(3).id());
        assertEquals("when", lines.get(3).code());
        assertEquals(4, lines.get(4).level());
        assertEquals("to", lines.get(4).type());
        assertEquals("to1", lines.get(4).id());
        assertEquals("to", lines.get(4).code());
        assertEquals(3, lines.get(5).level());
        assertEquals("when", lines.get(5).type());
        assertEquals("when2", lines.get(5).id());
        assertEquals("when", lines.get(5).code());
        assertEquals(4, lines.get(6).level());
        assertEquals("to", lines.get(6).type());
        assertEquals("to2", lines.get(6).id());
        assertEquals("to", lines.get(6).code());
        assertEquals(3, lines.get(7).level());
        assertEquals("otherwise", lines.get(7).type());
        assertEquals("otherwise1", lines.get(7).id());
        assertEquals("otherwise", lines.get(7).code());
        assertEquals(4, lines.get(8).level());
        assertEquals("to", lines.get(8).type());
        assertEquals("to3", lines.get(8).id());
        assertEquals("to", lines.get(8).code());
        assertEquals(2, lines.get(9).level());
        assertEquals("to", lines.get(9).type());
        assertEquals("to4", lines.get(9).id());
        assertEquals("to", lines.get(9).code());
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setDebugging(true);

                from("direct:start").routeId("myChoiceRoute")
                        .choice()
                            .when().xpath("$foo = 'bar'").to("mock:x")
                            .when().xpath("$foo = 'cheese'").to("mock:y")
                            .otherwise().to("mock:z")
                        .end()
                    .to("mock:end");
            }
        };
    }

}
