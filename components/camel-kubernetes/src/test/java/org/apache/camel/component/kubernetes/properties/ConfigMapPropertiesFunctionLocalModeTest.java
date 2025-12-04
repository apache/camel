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

package org.apache.camel.component.kubernetes.properties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

public class ConfigMapPropertiesFunctionLocalModeTest extends KubernetesTestSupport {

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").transform().simple("Hello ${body} we are at {{configmap:myconfig/bar.txt}}");
                from("direct:binary").transform().simple("File saved to {{configmap-binary:myconfig/binary.bin}}");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().addInitialProperty(ConfigMapPropertiesFunction.LOCAL_MODE, "true");
        context.getPropertiesComponent().addInitialProperty("myconfig/bar.txt", "The Local Bar");
        context.getPropertiesComponent()
                .addInitialProperty(
                        "myconfig/binary.bin",
                        Path.of(getClass()
                                        .getResource("/binary-example/binary.bin")
                                        .toURI())
                                .toAbsolutePath()
                                .toString());
        return context;
    }

    @Test
    @Order(1)
    public void configMapLocalMode() throws Exception {
        String out = template.requestBody("direct:start", "Jack", String.class);
        Assertions.assertEquals("Hello Jack we are at The Local Bar", out);
    }

    @Test
    @Order(2)
    public void configMapLocalModeUsingBinary() throws IOException {
        String out = template.requestBody("direct:binary", null, String.class);
        Assertions.assertTrue(out.matches("File saved to .*binary.bin"));
        Path filePath = Path.of(out.substring("File saved to ".length()));
        Assertions.assertArrayEquals(readExampleBinaryFile(), Files.readAllBytes(filePath));
    }
}
