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
import java.io.File;
import java.io.IOException;
import java.util.Base64;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.RouteDiagramDumper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultRouteDiagramDumperTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute")
                        .choice()
                        .when(simple("${header.foo} == 'bar'"))
                        .log("Got bar")
                        .otherwise()
                        .log("Got something else")
                        .end()
                        .to("mock:result");

                from("direct:other").routeId("otherRoute")
                        .to("log:other")
                        .to("mock:other");
            }
        };
    }

    @Test
    void testImage() {
        System.setProperty("java.awt.headless", "true");

        RouteDiagramDumper dumper = PluginHelper.getRouteDiagramDumper(context);
        BufferedImage image = dumper.dumpRoutesAsImage("*", RouteDiagramDumper.Theme.DARK);
        assertThat(image).isNotNull();
    }

    @Test
    void testImageToFile() throws IOException {
        System.setProperty("java.awt.headless", "true");

        File f = new File("target/deleteme.png");
        f.delete();

        RouteDiagramDumper dumper = PluginHelper.getRouteDiagramDumper(context);
        dumper.dumpRoutesToFile("*", RouteDiagramDumper.Theme.DARK, f);

        assertThat(f.exists());
    }

    @Test
    void testImageToFolder() throws IOException {
        System.setProperty("java.awt.headless", "true");

        File f = new File("target/dump");
        f.mkdirs();

        RouteDiagramDumper dumper = PluginHelper.getRouteDiagramDumper(context);
        dumper.dumpRoutesToFolder("*", RouteDiagramDumper.Theme.LIGHT, f);

        assertThat(f.exists());
        assertThat(f.list()).contains("myRoute.png", "otherRoute.png");
    }

    @Test
    void testBase64() throws Exception {
        System.setProperty("java.awt.headless", "true");

        RouteDiagramDumper dumper = PluginHelper.getRouteDiagramDumper(context);
        BufferedImage image = dumper.dumpRoutesAsImage("*", RouteDiagramDumper.Theme.DARK);
        assertThat(image).isNotNull();

        String base64 = dumper.imageToBase64(image);
        byte[] decoded = Base64.getDecoder().decode(base64);
        // PNG magic bytes
        assertThat(decoded[0] & 0xFF).isEqualTo(0x89);
        assertThat(decoded[1] & 0xFF).isEqualTo(0x50); // 'P'
        assertThat(decoded[2] & 0xFF).isEqualTo(0x4E); // 'N'
        assertThat(decoded[3] & 0xFF).isEqualTo(0x47); // 'G'
    }

}
