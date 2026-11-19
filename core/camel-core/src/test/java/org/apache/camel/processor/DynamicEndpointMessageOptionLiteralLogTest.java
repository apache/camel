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

import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.log.LogEndpoint;
import org.apache.camel.component.stub.StubEndpoint;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The option values of an endpoint whose uri is computed from the message are bound as literal text, also for a real
 * component that binds its options inside its own {@code createEndpoint}, with a generated configurer and typed options
 * ({@code log}). A {@code #class:} value is not created for a typed option (through the configurer and through
 * {@code resolveAndRemoveReferenceParameter}), a property placeholder stays literal (also for a bean that the component
 * configures with {@code setProperties(Object, Map)}), while {@code #bean:} and {@code #type:} in the static text of
 * the {@code toD} still resolve, and {@code #class:} in a static {@code to} still works.
 */
class DynamicEndpointMessageOptionLiteralLogTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        Properties prop = new Properties();
        prop.setProperty("secretTarget", "resolved");
        context.getPropertiesComponent().setInitialProperties(prop);
        context.getRegistry().bind("myFormatter", new RegisteredFormatter());
        // the log component configures this bean with the options of every log endpoint
        context.getRegistry().bind("logFormatter", new PrefixFormatter());
        return context;
    }

    @BeforeEach
    void resetCounter() {
        CountingFormatter.instances = 0;
    }

    @Test
    void classValueFromMessageIsNotCreatedForTypedOption() {
        Exchange out = template.send("direct:tod", e -> e.getMessage().setHeader("target",
                "log:m1?exchangeFormatter=#class:" + CountingFormatter.class.getName()));

        assertThat(out.getException()).isInstanceOf(ResolveEndpointFailedException.class);
        assertThat(CountingFormatter.instances).isZero();
    }

    @Test
    void classValueFromMessageIsNotCreatedForReferenceParameter() {
        // the log component resolves the logger option with resolveAndRemoveReferenceParameter
        Exchange out = template.send("direct:tod", e -> e.getMessage().setHeader("target",
                "log:m2?logger=#class:" + CountingFormatter.class.getName()));

        assertThat(out.getException()).isInstanceOf(ResolveEndpointFailedException.class);
        assertThat(CountingFormatter.instances).isZero();
    }

    @Test
    void placeholderFromMessageStaysLiteral() {
        template.sendBodyAndHeader("direct:marker", "Hello", "m", "{{secretTarget}}");

        assertThat(logEndpoint("m3").getMarker()).isEqualTo("{{secretTarget}}");
    }

    @Test
    void classValueFromMessageStaysLiteralForStringOption() {
        String value = "#class:" + CountingFormatter.class.getName();
        template.sendBodyAndHeader("direct:marker", "Hello", "m", value);

        assertThat(logEndpoint("m3").getMarker()).isEqualTo(value);
        assertThat(CountingFormatter.instances).isZero();
    }

    @Test
    void placeholderFromMessageStaysLiteralForComponentConfiguredBean() {
        // the log component binds the options to the logFormatter bean with setProperties(Object, Map)
        template.sendBodyAndHeader("direct:prefix", "Hello", "p", "{{secretTarget}}");

        PrefixFormatter formatter = context.getRegistry().lookupByNameAndType("logFormatter", PrefixFormatter.class);
        assertThat(formatter.getPrefix()).isEqualTo("{{secretTarget}}");
    }

    @Test
    void beanReferenceInStaticTextStillResolves() {
        template.sendBodyAndHeader("direct:bean", "Hello", "m", "audit");

        LogEndpoint endpoint = logEndpoint("b1");
        assertThat(endpoint.getExchangeFormatter()).isSameAs(context.getRegistry().lookupByName("myFormatter"));
        assertThat(endpoint.getMarker()).isEqualTo("audit");
    }

    @Test
    void typeReferenceInStaticTextStillResolves() {
        template.sendBodyAndHeader("direct:type", "Hello", "m", "audit");

        LogEndpoint endpoint = logEndpoint("t1");
        assertThat(endpoint.getExchangeFormatter()).isSameAs(context.getRegistry().lookupByName("myFormatter"));
    }

    @Test
    void classValueInStaticToStillCreated() {
        LogEndpoint endpoint = context.getEndpoint("log:s1?exchangeFormatter=#class:" + StaticFormatter.class.getName(),
                LogEndpoint.class);

        assertThat(endpoint.getExchangeFormatter()).isInstanceOf(StaticFormatter.class);
    }

    @Test
    void componentSetPropertiesOverrideIsUsed() {
        // the stub component turns off the binding of the options in its setProperties(Endpoint, Map) override
        template.sendBodyAndHeader("direct:tod", "Hello", "target", "stub:s2?timeout=1234");

        StubEndpoint endpoint = context.getEndpoints().stream()
                .filter(StubEndpoint.class::isInstance).map(StubEndpoint.class::cast)
                .filter(e -> e.getEndpointUri().contains("s2")).findFirst().orElseThrow();
        assertThat(endpoint.getTimeout()).isEqualTo(30000L);
    }

    private LogEndpoint logEndpoint(String name) {
        return context.getEndpoints().stream()
                .filter(LogEndpoint.class::isInstance).map(LogEndpoint.class::cast)
                .filter(e -> e.getEndpointUri().startsWith("log://" + name + "?"))
                .findFirst().orElseThrow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:tod").toD("${header.target}");
                from("direct:marker").toD("log:m3?marker=${header.m}");
                from("direct:prefix").toD("log:f1?prefix=${header.p}");
                from("direct:bean").toD("log:b1?exchangeFormatter=#bean:myFormatter&marker=${header.m}");
                from("direct:type").toD("log:t1?exchangeFormatter=#type:" + RegisteredFormatter.class.getName()
                                        + "&marker=${header.m}");
                from("direct:static").to("log:s1?exchangeFormatter=#class:" + StaticFormatter.class.getName());
            }
        };
    }

    /**
     * Counts its instances, so a {@code #class:} value that is created can be detected.
     */
    public static class CountingFormatter implements ExchangeFormatter {
        static volatile int instances;

        public CountingFormatter() {
            instances++;
        }

        @Override
        public String format(Exchange exchange) {
            return "counting";
        }
    }

    public static class StaticFormatter implements ExchangeFormatter {
        @Override
        public String format(Exchange exchange) {
            return "static";
        }
    }

    public static class RegisteredFormatter implements ExchangeFormatter {
        @Override
        public String format(Exchange exchange) {
            return "registered";
        }
    }

    public static class PrefixFormatter extends DefaultExchangeFormatter {
        private String prefix;

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }
    }
}
