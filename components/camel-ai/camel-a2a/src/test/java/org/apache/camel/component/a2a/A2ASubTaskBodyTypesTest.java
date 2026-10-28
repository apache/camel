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
package org.apache.camel.component.a2a;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.A2ASubTaskDefinition;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The body types of the a2aSubTask EIP (CAMEL-25928), checked at runtime: its steps start with the body as it was, and
 * the body after it is what its steps end with.
 */
class A2ASubTaskBodyTypesTest extends CamelTestSupport {

    /** The body the steps end with. */
    private static final String END = "end of the steps";

    private final List<Object> inside = new CopyOnWriteArrayList<>();

    A2ASubTaskBodyTypesTest() {
        testConfiguration().withUseRouteBuilder(false);
    }

    @Test
    void theStepsStartWithTheBodyAndTheBodyAfterIsWhatTheyEndWith() throws Exception {
        BodyTypes bodyTypes = A2ASubTaskDefinition.class.getAnnotation(BodyTypes.class);
        assertThat(bodyTypes).isNotNull();
        assertThat(bodyTypes.eipOut().from()).isEqualTo("steps");
        assertThat(bodyTypes.eipSteps().value()).containsExactly(Carrier.UNCHANGED);

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:end")
                        .a2aSubTask()
                        .emitBefore("Before ${body}")
                        .emitAfter("After ${body}")
                        .process(e -> inside.add(e.getMessage().getBody()))
                        .setBody(constant(END))
                        .end();

                from("direct:keep")
                        .a2aSubTask()
                        .emitBefore("Before ${body}")
                        .emitAfter("After ${body}")
                        .process(e -> inside.add(e.getMessage().getBody()))
                        .end();
            }
        });
        context.start();

        Order body = new Order();
        Exchange out = template.send("direct:end", e -> e.getMessage().setBody(body));
        assertThat(out.getException()).isNull();
        assertThat(inside).hasSize(1);
        assertThat(inside.get(0)).isSameAs(body);
        assertThat(out.getMessage().getBody()).isEqualTo(END);

        // steps that leave the body as it was give the same instance after the EIP
        inside.clear();
        out = template.send("direct:keep", e -> e.getMessage().setBody(body));
        assertThat(out.getException()).isNull();
        assertThat(inside).hasSize(1);
        assertThat(inside.get(0)).isSameAs(body);
        assertThat(out.getMessage().getBody()).isSameAs(body);
    }

    /** A body that no converter knows: an EIP that keeps it gives the same instance. */
    private static final class Order {
    }
}
