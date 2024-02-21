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
package org.apache.camel.component.kamelet;

import java.util.UUID;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KameletRouteDumpTest extends CamelTestSupport {

    @Override
    protected void postProcessTest() throws Exception {
        context().setDumpRoutes("xml");
        super.postProcessTest();
    }

    @Test
    public void canProduceToKamelet() {
        String body = UUID.randomUUID().toString();

        assertThat(
                fluentTemplate.toF("direct:templateEmbedded", body).request(String.class)).isEqualTo("test");
    }

    // **********************************************
    //
    // test set-up
    //
    // **********************************************

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("setBody")
                        .templateParameter("bodyValue")
                        .from("kamelet:source")
                        .setBody().constant("{{bodyValue}}")
                        .to("kamelet:sink");

                from("direct:templateEmbedded").id("test")
                        .kamelet("setBody?bodyValue=test")
                        .to("log:TEST?showAll=true&multiline=true");
            }
        };
    }
}
