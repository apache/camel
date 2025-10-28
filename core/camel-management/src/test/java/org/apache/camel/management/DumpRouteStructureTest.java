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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ModelDumpLine;
import org.apache.camel.spi.ModelToStructureDumper;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisabledOnOs(OS.AIX)
public class DumpRouteStructureTest extends ManagementTestSupport {

    @Test
    public void testDump() throws Exception {
        ModelToStructureDumper dumper = PluginHelper.getModelToStructureDumper(context);
        List<ModelDumpLine> lines = dumper.dumpStructure(context, context.getRoute("myOtherRoute"), false);
        assertEquals(5, lines.size());
        assertEquals(0, lines.get(0).level());
        assertEquals("route", lines.get(0).type());
        assertEquals("myOtherRoute", lines.get(0).id());
        assertEquals("route[myOtherRoute]", lines.get(0).code());
        assertEquals(1, lines.get(1).level());
        assertEquals("from", lines.get(1).type());
        assertEquals("myOtherRoute", lines.get(1).id());
        assertEquals("from[seda://bar?multipleConsumers=true&size=1234]", lines.get(1).code());
        assertEquals(2, lines.get(2).level());
        assertEquals("filter", lines.get(2).type());
        assertEquals("myfilter", lines.get(2).id());
        assertEquals("filter[header{bar}]", lines.get(2).code());
        assertEquals(3, lines.get(3).level());
        assertEquals("to", lines.get(3).type());
        assertEquals("mybar", lines.get(3).id());
        assertEquals("to[mock:bar]", lines.get(3).code());
        assertEquals(2, lines.get(4).level());
        assertEquals("to", lines.get(4).type());
        assertEquals("myend", lines.get(4).id());
        assertEquals("to[log:end]", lines.get(4).code());
    }

    @Test
    public void testDumpBrief() throws Exception {
        ModelToStructureDumper dumper = PluginHelper.getModelToStructureDumper(context);
        List<ModelDumpLine> lines = dumper.dumpStructure(context, context.getRoute("myOtherRoute"), true);
        assertEquals(5, lines.size());
        assertEquals(0, lines.get(0).level());
        assertEquals("route", lines.get(0).type());
        assertEquals("myOtherRoute", lines.get(0).id());
        assertEquals("route[myOtherRoute]", lines.get(0).code());
        assertEquals(1, lines.get(1).level());
        assertEquals("from", lines.get(1).type());
        assertEquals("myOtherRoute", lines.get(1).id());
        assertEquals("from[seda://bar]", lines.get(1).code());
        assertEquals(2, lines.get(2).level());
        assertEquals("filter", lines.get(2).type());
        assertEquals("myfilter", lines.get(2).id());
        assertEquals("filter", lines.get(2).code());
        assertEquals(3, lines.get(3).level());
        assertEquals("to", lines.get(3).type());
        assertEquals("mybar", lines.get(3).id());
        assertEquals("to", lines.get(3).code());
        assertEquals(2, lines.get(4).level());
        assertEquals("to", lines.get(4).type());
        assertEquals("myend", lines.get(4).id());
        assertEquals("to", lines.get(4).code());
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.setDebugging(true);

                from("seda:bar?size=1234&multipleConsumers=true").routeId("myOtherRoute")
                        .filter().header("bar").id("myfilter")
                            .to("mock:bar").id("mybar")
                        .end()
                        .to("log:end").id("myend");
            }
        };
    }

}
