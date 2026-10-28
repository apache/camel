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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.KameletDefinition;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The body types of the kamelet EIP (CAMEL-25928), checked at runtime. The catalog says UNKNOWN for the body after it,
 * as it is what the route of the Kamelet leaves: the same EIP gives a String with one Kamelet, keeps the body with
 * another and gives a List with a third.
 */
public class KameletBodyTypesTest extends CamelTestSupport {

    @Test
    public void theBodyAfterIsWhatTheKameletRouteLeaves() {
        BodyTypes bodyTypes = KameletDefinition.class.getAnnotation(BodyTypes.class);
        assertThat(bodyTypes).isNotNull();
        BodyType out = bodyTypes.eipOut();
        assertThat(out.value()).containsExactly(Carrier.UNKNOWN);
        assertThat(out.from()).isEmpty();

        Order body = new Order();

        Exchange text = template.send("direct:text", e -> e.getMessage().setBody(body));
        assertThat(text.getException()).isNull();
        assertThat(text.getMessage().getBody()).isEqualTo("Hello from the Kamelet");

        Exchange keep = template.send("direct:keep", e -> e.getMessage().setBody(body));
        assertThat(keep.getException()).isNull();
        assertThat(keep.getMessage().getBody()).isSameAs(body);

        Exchange list = template.send("direct:list", e -> e.getMessage().setBody(body));
        assertThat(list.getException()).isNull();
        assertThat(list.getMessage().getBody()).isEqualTo(List.of("a", "b"));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("text")
                        .from("kamelet:source")
                        .setBody(constant("Hello from the Kamelet"));

                routeTemplate("keep")
                        .from("kamelet:source")
                        .setHeader("visited", constant(true));

                routeTemplate("list")
                        .from("kamelet:source")
                        .setBody(constant(List.of("a", "b")));

                from("direct:text").kamelet("text");
                from("direct:keep").kamelet("keep");
                from("direct:list").kamelet("list");
            }
        };
    }

    /** A body that no converter knows: a Kamelet route that keeps it gives the same instance. */
    private static final class Order {
    }
}
