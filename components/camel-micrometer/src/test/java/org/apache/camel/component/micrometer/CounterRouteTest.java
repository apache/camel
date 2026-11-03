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
package org.apache.camel.component.micrometer;

import java.util.List;
import java.util.Map;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit6.CamelSpringTest;
import org.apache.camel.test.spring.junit6.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_COUNTER_DECREMENT;
import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_COUNTER_INCREMENT;
import static org.apache.camel.component.micrometer.MicrometerConstants.HEADER_METRIC_NAME;
import static org.apache.camel.component.micrometer.MicrometerConstants.METRICS_REGISTRY_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@CamelSpringTest
public class CounterRouteTest extends CamelSpringTestSupport {

    @EndpointInject("mock:out")
    private MockEndpoint endpoint;

    @Produce("direct:in-1")
    private ProducerTemplate producer1;

    @Produce("direct:in-2")
    private ProducerTemplate producer2;

    @Produce("direct:in-3")
    private ProducerTemplate producer3;

    @Produce("direct:in-4")
    private ProducerTemplate producer4;

    @Produce("direct:in-5")
    private ProducerTemplate producer5;

    @Produce("direct:in-6")
    private ProducerTemplate producer6;

    @Produce("direct:in-7")
    private ProducerTemplate producer7;

    @Produce("direct:in-8")
    private ProducerTemplate producer8;

    @BindToRegistry(METRICS_REGISTRY_NAME)
    private MeterRegistry registry = new SimpleMeterRegistry();

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new AnnotationConfigApplicationContext();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in-1")
                        .to("micrometer:counter:A?increment=5")
                        .to("mock:out");

                from("direct:in-2")
                        .to("micrometer:counter:B?decrement=9")
                        .to("mock:out");

                from("direct:in-3")
                        .setHeader(HEADER_COUNTER_INCREMENT, constant(417L))
                        .to("micrometer:counter:C")
                        .to("mock:out");

                from("direct:in-4")
                        .to("micrometer:counter:D?increment=${body.length}&tags.a=${body.length}")
                        .to("mock:out");

                from("direct:in-5")
                        .to("micrometer:counter:E?increment=${body}")
                        .to("mock:out");

                from("direct:in-6")
                        .to("micrometer:counter:F?increment=${header.inc}&decrement=${header.dec}")
                        .to("mock:out");

                from("direct:in-7")
                        .to("micrometer:counter:G")
                        .to("mock:out");

                from("direct:in-8")
                        .to("micrometer:counter:H?decrement=${body}")
                        .to("mock:out");
            }
        };
    }

    @Override
    public void doPostTearDown() {
        endpoint.reset();
    }

    @Test
    public void testOverrideMetricsName() throws Exception {
        endpoint.expectedMessageCount(1);
        producer1.sendBodyAndHeader(new Object(), HEADER_METRIC_NAME, "A1");
        assertEquals(5.0D, registry.find("A1").counter().count(), 0.01D);
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testOverrideIncrement() throws Exception {
        endpoint.expectedMessageCount(1);
        producer1.sendBodyAndHeader(new Object(), HEADER_COUNTER_INCREMENT, 14.0D);
        assertEquals(14.0D, registry.find("A").counter().count(), 0.01D);
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testOverrideDecrement() throws Exception {
        endpoint.expectedMessageCount(1);
        producer2.sendBodyAndHeader(new Object(), HEADER_COUNTER_DECREMENT, 7.0D);
        assertEquals(-7.0D, registry.find("B").counter().count(), 0.01D);
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testOverrideUsingConstantValue() throws Exception {
        endpoint.expectedMessageCount(1);
        producer3.sendBody(new Object());
        assertEquals(417.0D, registry.find("C").counter().count(), 0.01D);
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testUsingScriptEvaluation() throws Exception {
        endpoint.expectedMessageCount(1);
        String message = "Hello from Camel Metrics!";
        producer4.sendBody(message);
        Counter counter = registry.find("D").counter();
        assertEquals(message.length(), counter.count(), 0.01D);
        assertEquals(Integer.toString(message.length()), counter.getId().getTag("a"));
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testIncrementFromBody() throws Exception {
        endpoint.expectedMessageCount(1);
        producer5.sendBody("42");
        assertEquals(42.0D, registry.find("E").counter().count(), 0.01D);
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testIncrementFromBodyNotNumber() throws Exception {
        endpoint.expectedMessageCount(0);

        assertIncrementFails(Map.of("count", 5));
        assertIncrementFails(List.of(new Object()));
        assertIncrementFails(new Object());
        assertIncrementFails(null);

        // the counter is not updated (the exchange fails with IllegalArgumentException)
        assertEquals(0.0D, registry.find("E").counter().count(), 0.01D);
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testIncrementOrDecrementFromHeader() throws Exception {
        endpoint.expectedMessageCount(2);
        producer6.sendBodyAndHeader(null, "inc", 10.0D);
        producer6.sendBodyAndHeader(null, "dec", 3.0D);
        assertEquals(7.0D, registry.find("F").counter().count(), 0.01D);
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testIncrementOrDecrementFromHeaderMissing() throws Exception {
        endpoint.expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> producer6.sendBody("Hello"));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("The increment (${header.inc}) and decrement (${header.dec}) of counter F did not evaluate to a number",
                e.getCause().getMessage());

        assertEquals(0.0D, registry.find("F").counter().count(), 0.01D);
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testDecrementFromBodyNotNumber() throws Exception {
        endpoint.expectedMessageCount(0);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> producer8.sendBody(Map.of("count", 5)));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("The decrement (${body}) of counter H did not evaluate to a number",
                e.getCause().getMessage());

        assertEquals(0.0D, registry.find("H").counter().count(), 0.01D);
        endpoint.assertIsSatisfied();
    }

    @Test
    public void testNoIncrementOrDecrement() throws Exception {
        endpoint.expectedMessageCount(2);
        producer7.sendBody(new Object());
        producer7.sendBody(null);
        assertEquals(2.0D, registry.find("G").counter().count(), 0.01D);
        endpoint.assertIsSatisfied();
    }

    private void assertIncrementFails(Object body) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> producer5.sendBody(body));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertEquals("The increment (${body}) of counter E did not evaluate to a number",
                e.getCause().getMessage());
    }
}
