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

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KameletBasicTest extends CamelTestSupport {

    @Test
    public void canProduceToKamelet() {
        String body = UUID.randomUUID().toString();

        assertThat(
                fluentTemplate.toF("kamelet:setBody/test?bodyValue=%s", body).request(String.class)).isEqualTo(body);
    }

    @Test
    public void canConsumeFromKamelet() {
        assertThat(
                consumer.receiveBody("kamelet:tick", Integer.class)).isEqualTo(1);
    }

    @Test
    public void kameletCanBeCreatedWhileContextIsStarting() {
        assertThat(
                fluentTemplate.to("direct:templateEmbedded").request(String.class)).isEqualTo("embedded");
    }

    @Test
    public void kameletCanBeCreatedAfterContextIsStarted() throws Exception {
        String body = UUID.randomUUID().toString();

        RouteBuilder.addRoutes(context, b -> {
            b.from("direct:templateAfter")
                    .toF("kamelet:setBody/test?bodyValue=%s", body);
        });

        assertThat(
                fluentTemplate.to("direct:templateAfter").request(String.class)).isEqualTo(body);
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
                        .setBody().constant("{{bodyValue}}");

                routeTemplate("tick")
                        .from("timer:{{routeId}}?repeatCount=1&delay=-1&includeMetadata=true")
                        .setBody().exchangeProperty(Exchange.TIMER_COUNTER)
                        .to("kamelet:sink");

                from("direct:templateEmbedded")
                        .toF("kamelet:setBody/embedded?bodyValue=embedded");
            }
        };
    }
}
