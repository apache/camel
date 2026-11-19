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
package org.apache.camel.component.ai.tool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A {@code configuration} bean, on the component or on an endpoint, combines with the options set at the same level and
 * with the component's values: an option that is set wins over the bean's value, and a value the bean does not set is
 * taken from the option or from the component. The authorization guard of a tool is therefore the same whether its
 * options come from a configuration bean or not.
 */
class AiToolConfigurationBeanTest extends CamelTestSupport {

    static final class SubjectPolicy implements AuthorizationPolicy {
        private final String subject;

        SubjectPolicy(String subject) {
            this.subject = subject;
        }

        @Override
        public void beforeWrap(Route route, NamedNode definition) {
        }

        @Override
        public Processor wrap(Route route, Processor processor) {
            return exchange -> {
                if (!subject.equals(exchange.getProperty("subject", String.class))) {
                    throw new CamelAuthorizationException("caller is not authorized", exchange);
                }
                processor.process(exchange);
            };
        }
    }

    private final AiToolConfiguration componentDefaults = new AiToolConfiguration();
    private final AiToolConfiguration endpointDefaults = new AiToolConfiguration();
    private final AiToolConfiguration endpointDefaultsWithPolicy = new AiToolConfiguration();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        ctx.getRegistry().bind("alicePolicy", new SubjectPolicy("alice"));
        ctx.getRegistry().bind("carolPolicy", new SubjectPolicy("carol"));

        componentDefaults.setTags("t");
        ctx.getRegistry().bind("componentDefaults", componentDefaults);

        endpointDefaults.setTags("t");
        endpointDefaults.setDescription("From the bean");
        ctx.getRegistry().bind("endpointDefaults", endpointDefaults);

        endpointDefaultsWithPolicy.setTags("t");
        endpointDefaultsWithPolicy.setAuthorizationPolicy(new SubjectPolicy("carol"));
        ctx.getRegistry().bind("endpointDefaultsWithPolicy", endpointDefaultsWithPolicy);

        // bind the component options as Camel Main does for camel.component.ai-tool.*
        bindComponent(ctx, Map.of("configuration", "#componentDefaults", "authorizationPolicy", "#alicePolicy"));
        return ctx;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-tool:componentBean?description=Component bean")
                        .setBody(constant("ok"));
                from("ai-tool:endpointBean?configuration=#endpointDefaults")
                        .setBody(constant("ok"));
                from("ai-tool:endpointBeanAndPolicy?configuration=#endpointDefaults&authorizationPolicy=#carolPolicy")
                        .setBody(constant("ok"));
                from("ai-tool:endpointBeanWithPolicy?configuration=#endpointDefaultsWithPolicy&description=Bean policy")
                        .setBody(constant("ok"));
                from("ai-tool:endpointBeanAndRoles?configuration=#endpointDefaults&allowedRoles=finance")
                        .setBody(constant("ok"));
                from("ai-tool:endpointBeanAndDescription?configuration=#endpointDefaults&description=From the endpoint")
                        .setBody(constant("ok"));
            }
        };
    }

    @Test
    void componentPolicyIsKeptWithComponentConfigurationBean() {
        AiToolSpec spec = findSpec("componentBean");
        assertThat(call(spec, "bob")).isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(call(spec, "alice")).isInstanceOf(AiToolResult.Success.class);
    }

    @Test
    void componentPolicyIsKeptWithEndpointConfigurationBean() {
        AiToolSpec spec = findSpec("endpointBean");
        assertThat(spec.getDescription()).isEqualTo("From the bean");
        assertThat(call(spec, "bob")).isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(call(spec, "alice")).isInstanceOf(AiToolResult.Success.class);
    }

    @Test
    void endpointPolicyIsKeptWithEndpointConfigurationBean() {
        AiToolSpec spec = findSpec("endpointBeanAndPolicy");
        assertThat(call(spec, "alice")).isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(call(spec, "carol")).isInstanceOf(AiToolResult.Success.class);
    }

    @Test
    void policyOfEndpointConfigurationBeanReplacesComponentPolicy() {
        AiToolSpec spec = findSpec("endpointBeanWithPolicy");
        assertThat(spec.getDescription()).isEqualTo("Bean policy");
        assertThat(call(spec, "alice")).isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(call(spec, "carol")).isInstanceOf(AiToolResult.Success.class);
    }

    @Test
    void endpointAllowedRolesIsKeptWithEndpointConfigurationBean() {
        AiToolSpec spec = findSpec("endpointBeanAndRoles");
        // alice passes the component policy, but has not the role
        assertThat(call(spec, "alice")).isInstanceOf(AiToolResult.AuthorizationDenied.class);

        Exchange finance = new DefaultExchange(context);
        finance.setProperty("subject", "alice");
        finance.setProperty(AiToolConstants.SECURITY_PRINCIPAL_ROLES_PROPERTY, List.of("finance"));
        assertThat(AiToolExecutor.execute(spec, Map.of(), finance)).isInstanceOf(AiToolResult.Success.class);
    }

    @Test
    void endpointOptionWinsOverEndpointConfigurationBean() {
        assertThat(findSpec("endpointBeanAndDescription").getDescription()).isEqualTo("From the endpoint");
        // the bean is shared by several endpoints, and none of them changes it
        assertThat(endpointDefaults.getDescription()).isEqualTo("From the bean");
        assertThat(endpointDefaults.getAuthorizationPolicy()).isNull();
        assertThat(endpointDefaults.getAllowedRoles()).isNull();
    }

    @Test
    void componentOptionsWinOverComponentConfigurationBeanInAnyOrder() throws Exception {
        try (CamelContext other = new DefaultCamelContext()) {
            SubjectPolicy policy = new SubjectPolicy("alice");
            SubjectPolicy beanPolicy = new SubjectPolicy("carol");
            AiToolConfiguration bean = new AiToolConfiguration();
            bean.setTags("fromBean");
            bean.setDescription("From the bean");
            bean.setAuthorizationPolicy(beanPolicy);
            other.getRegistry().bind("bean", bean);
            other.getRegistry().bind("policy", policy);

            // a value that is not a reference is bound before the configuration, and a reference after it
            AiToolComponent component = bindComponent(other,
                    Map.of("configuration", "#bean", "authorizationPolicy", "#policy", "tags", "fromOption",
                            "allowedRoles", "finance"));

            AiToolConfiguration config = component.getConfiguration();
            assertThat(config.getAuthorizationPolicy()).isSameAs(policy);
            assertThat(config.getTags()).isEqualTo("fromOption");
            assertThat(config.getAllowedRoles()).isEqualTo("finance");
            assertThat(config.getDescription()).isEqualTo("From the bean");
        }
    }

    private static AiToolComponent bindComponent(CamelContext ctx, Map<String, Object> options) {
        AiToolComponent component = ctx.getComponent("ai-tool", AiToolComponent.class);
        PropertyBindingSupport.build()
                .withCamelContext(ctx)
                .withTarget(component)
                .withProperties(new HashMap<>(options))
                .withIgnoreCase(true)
                .bind();
        return component;
    }

    private Object call(AiToolSpec spec, String subject) {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty("subject", subject);
        return AiToolExecutor.execute(spec, Map.of(), exchange);
    }

    private AiToolSpec findSpec(String toolName) {
        return AiToolRegistry.getOrCreate(context).getToolsByTag("t").stream()
                .filter(s -> toolName.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + toolName));
    }
}
