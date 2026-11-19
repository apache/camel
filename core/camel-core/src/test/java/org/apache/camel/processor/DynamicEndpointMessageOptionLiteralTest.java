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
package org.apache.camel.processor;

import java.util.Map;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.PollingConsumerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Companion to {@link DynamicEndpointMessagePlaceholderTest}: the per-message recipient of the dynamic-uri EIPs is
 * computed from the message, so its query option values are also treated as literal text. A property-placeholder token
 * ({@code {{...}}}) or a bean/class reference token ({@code #class:}, {@code #bean:}, {@code #type:}) that appears in a
 * runtime-computed option value is kept verbatim: it is neither resolved, nor is any class instantiated or bean looked
 * up. See CAMEL-27160, which extends the CAMEL-24282 / CAMEL-24414 treatment from the uri path to the option binding.
 * <p/>
 * For {@code recipientList}, {@code routingSlip} and {@code dynamicRouter} the documented resolution of property
 * placeholders in the computed uri string is preserved (see {@link DynamicEndpointMessagePlaceholderTest}); only the
 * bean/class reference resolution of their runtime option values is brought in line, which is why those EIPs are
 * exercised here with reference tokens rather than placeholder tokens.
 */
class DynamicEndpointMessageOptionLiteralTest extends ContextTestSupport {

    private static final String MARKER_CLASS = "#class:" + Marker.class.getName();
    private static final String MARKER_BEAN = "#bean:marker";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        Properties prop = new Properties();
        prop.setProperty("secretTarget", "resolved");
        context.getPropertiesComponent().setInitialProperties(prop);
        context.addComponent("sink", new SinkComponent());
        context.addComponent("selfsink", new SelfBindingSinkComponent());
        context.getRegistry().bind("marker", new Marker());
        return context;
    }

    @BeforeEach
    void resetSink() {
        SinkEndpoint.lastOption = null;
        Marker.instances = 0;
    }

    @Test
    void toDOptionPlaceholderTokenStaysLiteral() throws Exception {
        getMockEndpoint("mock:done").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:tod", "Hello", "target", "sink:a?option={{secretTarget}}");

        assertMockEndpointsSatisfied();
        assertThat(SinkEndpoint.lastOption).isEqualTo("{{secretTarget}}");
    }

    @Test
    void toDOptionClassReferenceTokenStaysLiteral() throws Exception {
        getMockEndpoint("mock:done").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:tod", "Hello", "target", "sink:b?option=" + MARKER_CLASS);

        assertMockEndpointsSatisfied();
        assertThat(SinkEndpoint.lastOption).isEqualTo(MARKER_CLASS);
        assertThat(Marker.instances).isZero();
    }

    @Test
    void toDOptionBeanReferenceStillResolves() throws Exception {
        getMockEndpoint("mock:done").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:tod", "Hello", "target", "sink:c?option=" + MARKER_BEAN);

        assertMockEndpointsSatisfied();
        // a #bean: / #type: reference is a plain registry lookup of an already-registered bean, so it is still
        // resolved: this keeps references a route author wrote in a dynamic uri (such as dataSource=#ds) working.
        // Only property placeholders, #class: and #property: are kept literal.
        assertThat(SinkEndpoint.lastOption).isSameAs(context.getRegistry().lookupByName("marker"));
        assertThat(Marker.instances).isZero();
    }

    @Test
    void toDOptionPropertyReferenceStaysLiteral() throws Exception {
        getMockEndpoint("mock:done").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:tod", "Hello", "target", "sink:p?option=#property:secretTarget");

        assertMockEndpointsSatisfied();
        assertThat(SinkEndpoint.lastOption).isEqualTo("#property:secretTarget");
    }

    @Test
    void toDSelfBindingComponentClassReferenceStaysLiteral() throws Exception {
        getMockEndpoint("mock:done").expectedMessageCount(1);

        // selfsink binds its options inside its own createEndpoint, like log/aws2-s3/sql/jms and ~329 other
        // components; literal binding must be enforced there too (CAMEL-27160), so a #class: option value from the
        // message is not instantiated
        template.sendBodyAndHeader("direct:tod", "Hello", "target", "selfsink:a?option=" + MARKER_CLASS);

        assertMockEndpointsSatisfied();
        assertThat(SinkEndpoint.lastOption).isEqualTo(MARKER_CLASS);
        assertThat(Marker.instances).isZero();
    }

    @Test
    void toDSelfBindingComponentPlaceholderStaysLiteral() throws Exception {
        getMockEndpoint("mock:done").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:tod", "Hello", "target", "selfsink:b?option={{secretTarget}}");

        assertMockEndpointsSatisfied();
        assertThat(SinkEndpoint.lastOption).isEqualTo("{{secretTarget}}");
    }

    @Test
    void enrichOptionClassReferenceTokenStaysLiteral() throws Exception {
        getMockEndpoint("mock:done").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:en", "Hello", "target", "sink:d?option=" + MARKER_CLASS);

        assertMockEndpointsSatisfied();
        assertThat(SinkEndpoint.lastOption).isEqualTo(MARKER_CLASS);
        assertThat(Marker.instances).isZero();
    }

    @Test
    void pollEnrichOptionClassReferenceTokenStaysLiteral() throws Exception {
        getMockEndpoint("mock:done").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:pe", "Hello", "target", "sink:e?option=" + MARKER_CLASS);

        assertMockEndpointsSatisfied();
        assertThat(SinkEndpoint.lastOption).isEqualTo(MARKER_CLASS);
        assertThat(Marker.instances).isZero();
    }

    @Test
    void recipientListOptionClassReferenceTokenStaysLiteral() throws Exception {
        getMockEndpoint("mock:done").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:rl", "Hello", "target", "sink:f?option=" + MARKER_CLASS);

        assertMockEndpointsSatisfied();
        assertThat(SinkEndpoint.lastOption).isEqualTo(MARKER_CLASS);
        assertThat(Marker.instances).isZero();
    }

    @Test
    void routingSlipOptionClassReferenceTokenStaysLiteral() throws Exception {
        template.sendBodyAndHeader("direct:rs", "Hello", "target", "sink:g?option=" + MARKER_CLASS);

        assertThat(SinkEndpoint.lastOption).isEqualTo(MARKER_CLASS);
        assertThat(Marker.instances).isZero();
    }

    @Test
    void dynamicRouterOptionClassReferenceTokenStaysLiteral() throws Exception {
        template.sendBodyAndHeader("direct:dr", "Hello", "target", "sink:h?option=" + MARKER_CLASS);

        assertThat(SinkEndpoint.lastOption).isEqualTo(MARKER_CLASS);
        assertThat(Marker.instances).isZero();
    }

    /**
     * Dynamic router bean: routes to the header-supplied recipient on the first hop, then stops.
     */
    public String route(@Header("target") String target, @Header(Exchange.SLIP_ENDPOINT) String previous) {
        return previous == null ? target : null;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:tod").toD("${header.target}").to("mock:done");
                from("direct:en").enrich().simple("${header.target}")
                        .aggregationStrategy(AggregationStrategies.useOriginal()).to("mock:done");
                from("direct:pe").pollEnrich().simple("${header.target}").timeout(100).end().to("mock:done");
                from("direct:rl").recipientList(header("target")).to("mock:done");
                from("direct:rs").routingSlip(header("target"));
                from("direct:dr").dynamicRouter(method(DynamicEndpointMessageOptionLiteralTest.this, "route"));
            }
        };
    }

    /**
     * Marker whose public no-arg constructor counts instantiations, so a {@code #class:} reference that is wrongly
     * resolved can be detected.
     */
    public static class Marker {
        static volatile int instances;

        public Marker() {
            instances++;
        }
    }

    /**
     * A local in-JVM sink that records the value bound to its {@code option} property, so the test can assert that a
     * runtime-computed option value is kept literal.
     */
    private static final class SinkComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new SinkEndpoint(uri, this);
        }
    }

    /**
     * Like {@link SinkComponent} but binds its options inside its own {@code createEndpoint} (through the two-argument
     * {@code setProperties}), the pattern used by {@code log}, {@code aws2-s3}, {@code sql}, {@code jms} and ~329 other
     * components. It proves that literal binding is also enforced for these components (CAMEL-27160).
     */
    private static final class SelfBindingSinkComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
                throws Exception {
            SinkEndpoint endpoint = new SinkEndpoint(uri, this);
            setProperties(endpoint, parameters);
            return endpoint;
        }
    }

    private static final class SinkEndpoint extends DefaultEndpoint {
        static volatile Object lastOption;
        private Object option;

        SinkEndpoint(String uri, DefaultComponent component) {
            super(uri, component);
        }

        public Object getOption() {
            return option;
        }

        public void setOption(Object option) {
            this.option = option;
            lastOption = option;
        }

        @Override
        public Producer createProducer() {
            return new DefaultProducer(this) {
                @Override
                public void process(Exchange exchange) {
                    // no-op sink
                }
            };
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PollingConsumer createPollingConsumer() {
            return new NoopPollingConsumer(this);
        }

        @Override
        public boolean isSingleton() {
            return false;
        }
    }

    private static final class NoopPollingConsumer extends PollingConsumerSupport {
        NoopPollingConsumer(Endpoint endpoint) {
            super(endpoint);
        }

        @Override
        public Exchange receive() {
            return null;
        }

        @Override
        public Exchange receive(long timeout) {
            return null;
        }

        @Override
        public Exchange receiveNoWait() {
            return null;
        }

        @Override
        protected void doStart() {
            // noop
        }

        @Override
        protected void doStop() {
            // noop
        }
    }
}
