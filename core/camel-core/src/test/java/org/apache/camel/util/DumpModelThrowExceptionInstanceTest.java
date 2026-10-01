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
package org.apache.camel.util;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An exception given to throwException as an instance is dumped as its type and message.
 */
public class DumpModelThrowExceptionInstanceTest extends DumpModelAsYamlTestSupport {

    @Test
    public void testDumpModelAsYaml() throws Exception {
        String out = PluginHelper.getModelToYAMLDumper(context).dumpModelAsYaml(context, context.getRouteDefinition("myRoute"));
        assertThat(out).contains("- throwException:", """
                            message: Out of stock
                            exceptionType: java.lang.IllegalStateException
                """);
        String typed = PluginHelper.getModelToYAMLDumper(context).dumpModelAsYaml(context, context.getRouteDefinition("typed"));
        assertThat(typed).contains("- throwException:", """
                            message: Sold out
                            exceptionType: java.lang.IllegalArgumentException
                """);
    }

    @Test
    public void testDumpModelAsXml() throws Exception {
        String out = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, context.getRouteDefinition("myRoute"));
        assertThat(out).contains(
                "message=\"Out of stock\" exceptionType=\"java.lang.IllegalStateException\"/>");
        String typed = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context, context.getRouteDefinition("typed"));
        assertThat(typed).contains(
                "message=\"Sold out\" exceptionType=\"java.lang.IllegalArgumentException\"/>");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute")
                        .throwException(new IllegalStateException("Out of stock"));

                from("direct:typed").routeId("typed")
                        .throwException(IllegalArgumentException.class, "Sold out");
            }
        };
    }
}
