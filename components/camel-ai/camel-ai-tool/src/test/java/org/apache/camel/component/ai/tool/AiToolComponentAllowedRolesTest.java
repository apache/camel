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

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code allowedRoles} and {@code allRolesRequired} set on the ai-tool component are the default for every tool route.
 * An endpoint's own value replaces the component's; only an option that is not set at all takes the component's value.
 */
class AiToolComponentAllowedRolesTest extends CamelTestSupport {

    /** Allows every call (delegates straight to the route). */
    static final class AllowAll implements AuthorizationPolicy {
        @Override
        public void beforeWrap(Route route, NamedNode definition) {
            // nothing to prepare
        }

        @Override
        public Processor wrap(Route route, Processor processor) {
            return processor::process;
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        ctx.getRegistry().bind("allowAll", new AllowAll());
        ctx.getPropertiesComponent().addInitialProperty("roles.empty", "");
        AiToolComponent component = ctx.getComponent("ai-tool", AiToolComponent.class);
        component.getConfiguration().setAllowedRoles("finance,controlling");
        component.getConfiguration().setAllRolesRequired(true);
        return ctx;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-tool:inherits?tags=t&description=Inherits both options")
                        .setBody(constant("ok"));
                from("ai-tool:ownRoles?tags=t&description=Names its own roles&allowedRoles=sales,marketing")
                        .setBody(constant("ok"));
                from("ai-tool:anyRole?tags=t&description=Asks for one of the roles&allRolesRequired=false")
                        .setBody(constant("ok"));
                from("ai-tool:blankRoles?tags=t&description=Names no role&allowedRoles=")
                        .setBody(constant("ok"));
                from("ai-tool:placeholderRoles?tags=t&description=Roles from an empty property"
                     + "&allowedRoles={{roles.empty}}")
                        .setBody(constant("ok"));
                from("ai-tool:optionalRoles?tags=t&description=Roles from an optional property that is absent"
                     + "&allowedRoles={{?roles.absent}}")
                        .setBody(constant("ok"));
                from("ai-tool:ownPolicy?tags=t&description=Sets only its own policy&authorizationPolicy=#allowAll")
                        .setBody(constant("ok"));
            }
        };
    }

    @Test
    void theComponentOptionsGuardRoutesWithoutAnEndpointOption() {
        AiToolSpec spec = findSpec("inherits");

        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("finance")))
                .isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("finance", "controlling")))
                .isInstanceOf(AiToolResult.Success.class);
    }

    @Test
    void theEndpointRolesReplaceTheComponentRoles() {
        AiToolSpec spec = findSpec("ownRoles");

        // the endpoint's roles are not added to the component's: finance and controlling no longer count
        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("finance", "controlling")))
                .isInstanceOf(AiToolResult.AuthorizationDenied.class);
        // the component's allRolesRequired applies to the endpoint's own list
        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("sales")))
                .isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("sales", "marketing")))
                .isInstanceOf(AiToolResult.Success.class);
    }

    @Test
    void theEndpointAllRolesRequiredReplacesTheComponentValue() {
        AiToolSpec spec = findSpec("anyRole");

        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("controlling")))
                .isInstanceOf(AiToolResult.Success.class);
        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("sales")))
                .isInstanceOf(AiToolResult.AuthorizationDenied.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "blankRoles", "placeholderRoles" })
    void anEndpointValueThatNamesNoRoleRefusesEveryCall(String toolName) {
        // an empty value, also the one an empty property placeholder resolves to, is the endpoint's own value: it
        // neither opens the tool nor falls back to the roles of the component
        AiToolSpec spec = findSpec(toolName);

        assertThat(AiToolExecutor.execute(spec, Map.of(), new DefaultExchange(context)))
                .isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("finance", "controlling")))
                .isInstanceOf(AiToolResult.AuthorizationDenied.class);
    }

    @Test
    void anOptionalPlaceholderWhosePropertyIsAbsentTakesTheComponentRoles() {
        // {{?name}} without the property removes the option: it is not set, as when it is not written at all
        AiToolSpec spec = findSpec("optionalRoles");

        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("finance")))
                .isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("finance", "controlling")))
                .isInstanceOf(AiToolResult.Success.class);
    }

    @Test
    void aComponentValueThatNamesNoRoleClosesEveryToolThatNamesNone() throws Exception {
        try (DefaultCamelContext closed = new DefaultCamelContext()) {
            closed.getComponent("ai-tool", AiToolComponent.class).getConfiguration().setAllowedRoles("");
            closed.addRoutes(new RouteBuilder() {
                public void configure() {
                    from("ai-tool:namesNone?tags=t&description=Names no role itself")
                            .setBody(constant("ok"));
                    from("ai-tool:namesItsOwn?tags=t&description=Names its own role&allowedRoles=finance")
                            .setBody(constant("ok"));
                }
            });
            closed.start();

            assertThat(AiToolExecutor.execute(findSpec(closed, "namesNone"), Map.of(), finance(closed)))
                    .isInstanceOf(AiToolResult.AuthorizationDenied.class);
            assertThat(AiToolExecutor.execute(findSpec(closed, "namesItsOwn"), Map.of(), finance(closed)))
                    .isInstanceOf(AiToolResult.Success.class);
        }
    }

    private static Exchange finance(CamelContext camelContext) {
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.setProperty(AiToolConstants.SECURITY_PRINCIPAL_ROLES_PROPERTY, List.of("finance"));
        return exchange;
    }

    @Test
    void theComponentRolesApplyToAToolThatSetsOnlyItsOwnPolicy() {
        AiToolSpec spec = findSpec("ownPolicy");

        // the policy allows every call, so it is the component's roles that refuse this one
        assertThat(AiToolExecutor.execute(spec, Map.of(), new DefaultExchange(context)))
                .isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("finance", "controlling")))
                .isInstanceOf(AiToolResult.Success.class);
    }

    private Exchange callerWithRoles(String... roles) {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(AiToolConstants.SECURITY_PRINCIPAL_ROLES_PROPERTY, List.of(roles));
        return exchange;
    }

    private AiToolSpec findSpec(String toolName) {
        return findSpec(context, toolName);
    }

    private static AiToolSpec findSpec(CamelContext camelContext, String toolName) {
        return AiToolRegistry.getOrCreate(camelContext).getToolsByTag("t").stream()
                .filter(s -> toolName.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + toolName));
    }
}
