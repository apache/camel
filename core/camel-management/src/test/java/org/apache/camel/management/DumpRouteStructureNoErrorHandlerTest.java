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

/**
 * A route with noErrorHandler runs none of its onException clauses (a global one is copied into every route), so its
 * structure leaves them out.
 */
@DisabledOnOs(OS.AIX)
public class DumpRouteStructureNoErrorHandlerTest extends ManagementTestSupport {

    @Test
    public void testDump() throws Exception {
        ModelToStructureDumper dumper = PluginHelper.getModelToStructureDumper(context);

        List<String> checkout = types(dumper.dumpStructure(context, "checkout", false));
        assertEquals(List.of("route", "from", "onException", "to", "to"), checkout);

        List<String> provider = types(dumper.dumpStructure(context, "provider", false));
        assertEquals(List.of("route", "from", "throwException"), provider);
    }

    private static List<String> types(List<ModelDumpLine> lines) {
        return lines.stream().map(ModelDumpLine::type).toList();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(IllegalStateException.class).handled(true).to("mock:parked");

                from("direct:checkout").routeId("checkout")
                        .to("direct:provider");

                from("direct:provider").routeId("provider").errorHandler(noErrorHandler())
                        .throwException(new IllegalStateException("declined"));
            }
        };
    }
}
