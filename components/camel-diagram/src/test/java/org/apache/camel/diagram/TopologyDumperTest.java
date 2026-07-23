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
package org.apache.camel.diagram;

import java.awt.image.BufferedImage;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.RouteDiagramDumper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TopologyDumperTest extends CamelTestSupport {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("timer:tick?period=5000").routeId("generator")
                        .setBody(constant("order"))
                        .to("direct:process");

                from("direct:process").routeId("processor")
                        .log("Processing: ${body}")
                        .to("direct:validate")
                        .to("mock:result");

                from("direct:validate").routeId("validator")
                        .log("Validating: ${body}");

                from("direct:isolated").routeId("isolated")
                        .to("mock:isolated");
            }
        };
    }

    @Test
    void testTopologyAsciiArt() {
        RouteDiagramDumper dumper = PluginHelper.getRouteDiagramDumper(context);
        String ascii = dumper.dumpTopologyAsAsciiArt(180, false);

        assertThat(ascii).isNotEmpty();
        assertThat(ascii).contains("generator");
        assertThat(ascii).contains("processor");
        assertThat(ascii).contains("validator");
        assertThat(ascii).contains("isolated");
    }

    @Test
    void testTopologyUnicodeArt() {
        RouteDiagramDumper dumper = PluginHelper.getRouteDiagramDumper(context);
        String unicode = dumper.dumpTopologyAsAsciiArt(180, true);

        assertThat(unicode).isNotEmpty();
        assertThat(unicode).contains("generator");
        assertThat(unicode).contains("┌");
    }

    @Test
    void testTopologyImage() {
        RouteDiagramDumper dumper = PluginHelper.getRouteDiagramDumper(context);
        BufferedImage image = dumper.dumpTopologyAsImage(
                RouteDiagramDumper.Theme.DARK, false, 180, 12);

        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isGreaterThan(0);
        assertThat(image.getHeight()).isGreaterThan(0);
    }

}
