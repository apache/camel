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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.AuthorizationPolicy;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The {@code allowedRoles} option of an {@code ai-tool} route: the caller needs one of the named roles, or all of them
 * with {@code allRolesRequired}. The caller's roles are read only from the {@code CamelAiToolSecurityPrincipalRoles}
 * exchange property, and a refusal takes the path of an {@code authorizationPolicy} denial: a short refusal to the
 * model, and an {@link AiToolAuthorizationDeniedEvent}.
 */
class AiToolAllowedRolesTest extends CamelTestSupport {

    private final List<AiToolAuthorizationDeniedEvent> events = new CopyOnWriteArrayList<>();
    private final AtomicInteger policyCalls = new AtomicInteger();

    /** Counts the calls that reach it, and allows the call only when the exchange carries {@code subject == alice}. */
    final class CountingSubjectPolicy implements AuthorizationPolicy {
        @Override
        public void beforeWrap(Route route, NamedNode definition) {
            // nothing to prepare
        }

        @Override
        public Processor wrap(Route route, Processor processor) {
            return exchange -> {
                policyCalls.incrementAndGet();
                if (!"alice".equals(exchange.getProperty("subject", String.class))) {
                    throw new CamelAuthorizationException("caller is not authorized", exchange);
                }
                processor.process(exchange);
            };
        }
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("subjectPolicy", new CountingSubjectPolicy());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("ai-tool:orderCosts?tags=test&description=Costs of a production order&allowedRoles=finance")
                        .setBody(constant("ok"));

                from("ai-tool:anyOf?tags=test&description=One of two roles&allowedRoles=finance,controlling")
                        .setBody(constant("ok"));

                from("ai-tool:allOf?tags=test&description=Both roles&allowedRoles=finance,controlling"
                     + "&allRolesRequired=true")
                        .setBody(constant("ok"));

                from("ai-tool:withArgument?tags=test&description=Takes an argument named roles&allowedRoles=finance"
                     + "&parameter.roles=string")
                        .setBody(constant("ok"));

                from("ai-tool:withPolicy?tags=test&description=Roles and a policy&allowedRoles=finance"
                     + "&authorizationPolicy=#subjectPolicy")
                        .setBody(constant("ok"));

                from("ai-tool:allRolesRequiredAlone?tags=test&description=Names no roles&allRolesRequired=true")
                        .setBody(constant("ok"));
            }
        };
    }

    @BeforeEach
    void registerEventNotifier() {
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof AiToolAuthorizationDeniedEvent denied) {
                    events.add(denied);
                }
            }
        });
    }

    @Test
    void aCallerWithTheRoleMayCall() {
        AiToolResult result = AiToolExecutor.execute(findSpec("orderCosts"), Map.of(), callerWithRoles("sales", "finance"));

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
        assertThat(((AiToolResult.Success) result).value()).isEqualTo("ok");
        assertThat(events).isEmpty();
    }

    @Test
    void aCallerWithoutTheRoleIsRefused() {
        AiToolResult result = AiToolExecutor.execute(findSpec("orderCosts"), Map.of(), callerWithRoles("sales"));

        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
    }

    @Test
    void theRoleNameIsComparedExactly() {
        AiToolResult result = AiToolExecutor.execute(findSpec("orderCosts"), Map.of(),
                callerWithRoles("Finance", "finance-admin", " finance"));

        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
    }

    @Test
    void anyOneOfTheRolesIsEnough() {
        AiToolSpec spec = findSpec("anyOf");

        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("controlling")))
                .isInstanceOf(AiToolResult.Success.class);
        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("finance")))
                .isInstanceOf(AiToolResult.Success.class);
        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("sales")))
                .isInstanceOf(AiToolResult.AuthorizationDenied.class);
    }

    @Test
    void allRolesRequiredNeedsEveryRole() {
        AiToolSpec spec = findSpec("allOf");

        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("finance")))
                .isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("controlling", "sales", "finance")))
                .isInstanceOf(AiToolResult.Success.class);
    }

    @Test
    void theEntriesAreTrimmedAndBlankEntriesAreIgnored() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                AiToolEndpoint endpoint = getContext().getEndpoint(
                        "ai-tool:spaced?tags=test&description=Roles written with spaces&allRolesRequired=true",
                        AiToolEndpoint.class);
                endpoint.getConfiguration().setAllowedRoles(" finance , ,controlling,, ");
                from(endpoint).setBody(constant("ok"));
            }
        });
        AiToolSpec spec = findSpec("spaced");

        // every role is needed: a blank entry that counted as a role would refuse this caller
        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("finance", "controlling")))
                .isInstanceOf(AiToolResult.Success.class);
        assertThat(AiToolExecutor.execute(spec, Map.of(), callerWithRoles("finance")))
                .isInstanceOf(AiToolResult.AuthorizationDenied.class);
    }

    @Test
    void aCallWithoutTheRolesPropertyIsRefused() {
        AiToolResult result = AiToolExecutor.execute(findSpec("orderCosts"), Map.of(), new DefaultExchange(context));

        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
    }

    @Test
    void aRolesPropertyThatIsNotACollectionIsRefused() {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(AiToolConstants.SECURITY_PRINCIPAL_ROLES_PROPERTY, "finance");

        AiToolResult result = AiToolExecutor.execute(findSpec("orderCosts"), Map.of(), exchange);

        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
        // the log points at the property, which someone set to another type
        assertThat(((AiToolResult.AuthorizationDenied) result).cause().getMessage())
                .contains("orderCosts", "finance", AiToolConstants.SECURITY_PRINCIPAL_ROLES_PROPERTY);
    }

    @Test
    void aRoleInAHeaderOrAnArgumentDoesNotCount() {
        // on a tool route the headers carry the tool arguments, which the model controls
        Exchange exchange = callerWithRoles("sales");
        exchange.getMessage().setHeader(AiToolConstants.SECURITY_PRINCIPAL_ROLES_PROPERTY, List.of("finance"));

        AiToolResult result = AiToolExecutor.execute(findSpec("withArgument"), Map.of("roles", "finance"), exchange);

        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
    }

    @Test
    void aRoleInAHeaderOrAnArgumentDoesNotStandInForAMissingProperty() {
        // no roles property at all: the header and the argument must not be read in its place
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(AiToolConstants.SECURITY_PRINCIPAL_ROLES_PROPERTY, List.of("finance"));

        AiToolResult result = AiToolExecutor.execute(findSpec("withArgument"), Map.of("roles", "finance"), exchange);

        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
    }

    @Test
    void theRolesTheCallingRouteSetReachTheTool() {
        // langchain4j-agent, openai and spring-ai-chat run the tool on a copy of the calling exchange, so the roles
        // the calling route set before the agent ran are the ones the tool checks
        Exchange toolExchange = AiToolExecutor.createToolExchange(callerWithRoles("finance"));

        AiToolResult result = AiToolExecutor.execute(findSpec("orderCosts"), Map.of(), toolExchange);

        assertThat(result).isInstanceOf(AiToolResult.Success.class);
    }

    @Test
    void theRefusalNamesTheToolTheRolesAndTheCallerInTheLogOnly() {
        Exchange exchange = callerWithRoles("sales");
        exchange.setProperty(AiToolConstants.SECURITY_PRINCIPAL_NAME_PROPERTY, "alice");

        AiToolResult result = AiToolExecutor.execute(findSpec("anyOf"), Map.of(), exchange);

        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
        AiToolResult.AuthorizationDenied denied = (AiToolResult.AuthorizationDenied) result;
        // the model is told neither the roles nor the caller
        assertThat(denied.message()).contains("anyOf").doesNotContain("finance", "controlling", "alice");
        // the exception, which AiToolExecutor logs at WARN, says what the tool asks for and who called
        assertThat(denied.cause()).isInstanceOf(CamelAuthorizationException.class);
        assertThat(denied.cause().getMessage()).contains("anyOf", "finance", "controlling", "alice");
        assertThat(((CamelAuthorizationException) denied.cause()).getPolicyId()).isEqualTo("allowedRoles");
    }

    @Test
    void theRefusalSaysWhenTheCallerHasNoRoles() {
        // the MCP bridge leaves the property out for a caller without roles: an ordinary caller, not a wiring fault
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(AiToolConstants.SECURITY_PRINCIPAL_NAME_PROPERTY, "alice");

        AiToolResult result = AiToolExecutor.execute(findSpec("allOf"), Map.of(), exchange);

        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(((AiToolResult.AuthorizationDenied) result).cause().getMessage())
                .contains("allOf", "finance", "controlling", "alice", "no roles")
                .doesNotContain(AiToolConstants.SECURITY_PRINCIPAL_ROLES_PROPERTY);
    }

    @Test
    void aRefusalFiresTheDeniedEvent() {
        Exchange exchange = callerWithRoles("sales");

        AiToolResult result = AiToolExecutor.execute(findSpec("orderCosts"), Map.of(), exchange);

        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(events).hasSize(1);
        AiToolAuthorizationDeniedEvent event = events.get(0);
        assertThat(event.getToolName()).isEqualTo("orderCosts");
        assertThat(event.getExchange()).isSameAs(exchange);
        assertThat(event.getCause()).isInstanceOf(CamelAuthorizationException.class);
        assertThat(event.getCause().getPolicyId()).isEqualTo("allowedRoles");
    }

    @Test
    void withAPolicyBothMustPass() {
        AiToolSpec spec = findSpec("withPolicy");

        Exchange alice = callerWithRoles("finance");
        alice.setProperty("subject", "alice");
        assertThat(AiToolExecutor.execute(spec, Map.of(), alice)).isInstanceOf(AiToolResult.Success.class);
        assertThat(policyCalls).hasValue(1);

        // bob has the role, so the call reaches the policy, which refuses him
        Exchange bob = callerWithRoles("finance");
        bob.setProperty("subject", "bob");
        AiToolResult result = AiToolExecutor.execute(spec, Map.of(), bob);
        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(((AiToolResult.AuthorizationDenied) result).cause().getMessage()).contains("caller is not authorized");
        assertThat(policyCalls).hasValue(2);
    }

    @Test
    void aCallThatFailsTheRolesDoesNotReachThePolicy() {
        Exchange alice = callerWithRoles("sales");
        alice.setProperty("subject", "alice");

        AiToolResult result = AiToolExecutor.execute(findSpec("withPolicy"), Map.of(), alice);

        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(policyCalls).hasValue(0);
    }

    @Test
    void allRolesRequiredAloneHasNoEffect() {
        AiToolSpec spec = findSpec("allRolesRequiredAlone");

        assertThat(AiToolExecutor.execute(spec, Map.of(), new DefaultExchange(context)))
                .isInstanceOf(AiToolResult.Success.class);
        // no guard is put in front of the route
        AiToolConsumer consumer = (AiToolConsumer) spec.getConsumer();
        assertThat(consumer.getToolProcessor()).isSameAs(consumer.getProcessor());
    }

    @Test
    void failsClosedWhileTheGuardIsNotInPlace() throws Exception {
        // a consumer that is not started has no guard yet: it must refuse the call, also for a caller who has the
        // role, rather than hand out the unguarded route processor
        AtomicBoolean routeRan = new AtomicBoolean();
        AiToolEndpoint endpoint
                = context.getEndpoint("ai-tool:notStarted?tags=test&allowedRoles=finance", AiToolEndpoint.class);
        AiToolConsumer consumer = (AiToolConsumer) endpoint.createConsumer(exchange -> routeRan.set(true));
        Exchange exchange = callerWithRoles("finance");

        assertThatThrownBy(() -> consumer.getToolProcessor().process(exchange))
                .isInstanceOf(CamelAuthorizationException.class);
        assertThat(routeRan).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", ",", " , " })
    void anAllowedRolesThatNamesNoRoleRefusesEveryCall(String allowedRoles) throws Exception {
        // an empty value is what an empty property placeholder gives. The route starts, so one such tool does not
        // stop the others.
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:namesNoRole?tags=test&description=Names no role&allowedRoles=" + allowedRoles)
                        .setBody(constant("ok"));
            }
        });

        AiToolResult result = AiToolExecutor.execute(findSpec("namesNoRole"), Map.of(), callerWithRoles("finance"));

        assertThat(result).isInstanceOf(AiToolResult.AuthorizationDenied.class);
        AiToolResult.AuthorizationDenied denied = (AiToolResult.AuthorizationDenied) result;
        assertThat(denied.cause().getMessage()).contains("namesNoRole", "names no role");
        assertThat(((CamelAuthorizationException) denied.cause()).getPolicyId()).isEqualTo("allowedRoles");
    }

    @Test
    void anAllowedRolesThatNamesNoRoleRefusesEveryCallWithAllRolesRequiredToo() throws Exception {
        // "all of no roles" would pass if the empty list were checked like any other
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:namesNoRole?tags=test&description=Names no role&allowedRoles=,&allRolesRequired=true")
                        .setBody(constant("ok"));
            }
        });

        assertThat(AiToolExecutor.execute(findSpec("namesNoRole"), Map.of(), callerWithRoles("finance")))
                .isInstanceOf(AiToolResult.AuthorizationDenied.class);
        assertThat(AiToolExecutor.execute(findSpec("namesNoRole"), Map.of(), new DefaultExchange(context)))
                .isInstanceOf(AiToolResult.AuthorizationDenied.class);
    }

    @Test
    void theRolesCheckDoesNotStopTheProcessorItGuards() {
        // the processor it guards belongs to the route, which starts and stops it
        LifecycleProcessor routeProcessor = new LifecycleProcessor();
        routeProcessor.start();
        AiToolAllowedRolesProcessor check
                = new AiToolAllowedRolesProcessor(routeProcessor, "orderCosts", List.of("finance"), false);

        ServiceHelper.startService(check);
        ServiceHelper.stopService(check);

        assertThat(routeProcessor.isStarted()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "&allowedRoles=finance" })
    void theConsumerStartsAndStopsTheProcessorThePolicyWrapped(String allowedRoles) throws Exception {
        // with allowedRoles the roles check wraps the policy's processor, and does not start or stop it itself
        LifecycleProcessor policyProcessor = new LifecycleProcessor();
        context.getRegistry().bind("lifecyclePolicy", new AuthorizationPolicy() {
            @Override
            public void beforeWrap(Route route, NamedNode definition) {
                // nothing to prepare
            }

            @Override
            public Processor wrap(Route route, Processor processor) {
                return policyProcessor;
            }
        });
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("ai-tool:lifecycle?tags=test&description=A policy with a lifecycle"
                     + "&authorizationPolicy=#lifecyclePolicy" + allowedRoles)
                        .routeId("lifecycle")
                        .setBody(constant("ok"));
            }
        });
        assertThat(policyProcessor.isStarted()).isTrue();

        context.getRouteController().stopRoute("lifecycle");

        assertThat(policyProcessor.isStarted()).isFalse();
        assertThat(policyProcessor.isStopped()).isTrue();
    }

    /** A processor whose lifecycle the test reads. */
    static final class LifecycleProcessor extends ServiceSupport implements Processor {
        @Override
        public void process(Exchange exchange) {
            // the tests only start and stop it
        }
    }

    private Exchange callerWithRoles(String... roles) {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(AiToolConstants.SECURITY_PRINCIPAL_ROLES_PROPERTY, List.of(roles));
        return exchange;
    }

    private AiToolSpec findSpec(String toolName) {
        return AiToolRegistry.getOrCreate(context).getToolsByTag("test").stream()
                .filter(s -> toolName.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + toolName));
    }
}
