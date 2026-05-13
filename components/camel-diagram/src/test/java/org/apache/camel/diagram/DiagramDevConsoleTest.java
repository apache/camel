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

import java.util.Base64;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagramDevConsoleTest extends CamelTestSupport {

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

    private DevConsole resolveConsole() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-diagram");
        assertThat(console).as("route-diagram console should be resolvable").isNotNull();
        assertThat(console.getId()).isEqualTo("route-diagram");
        return console;
    }

    @Test
    void testTextOutputDefault() {
        System.setProperty("java.awt.headless", "true");
        DevConsole console = resolveConsole();
        String text = (String) console.call(DevConsole.MediaType.TEXT);
        assertThat(text).isNotNull();
        // default theme renders HTML with inline image
        assertThat(text).contains("<html>");
        assertThat(text).contains("data:image/png;base64,");
    }

    @Test
    void testJsonOutput() {
        System.setProperty("java.awt.headless", "true");
        DevConsole console = resolveConsole();
        JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON);
        assertThat(json).isNotNull();
        assertThat(json).containsKey("image");

        String image = json.getString("image");
        assertThat(image).isNotEmpty();
        byte[] decoded = Base64.getDecoder().decode(image);
        // PNG magic bytes
        assertThat(decoded[0] & 0xFF).isEqualTo(0x89);
        assertThat(decoded[1] & 0xFF).isEqualTo(0x50); // 'P'
        assertThat(decoded[2] & 0xFF).isEqualTo(0x4E); // 'N'
        assertThat(decoded[3] & 0xFF).isEqualTo(0x47); // 'G'
    }
}
