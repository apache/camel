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
package org.apache.camel.component.cedar;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cedarpolicy.AuthorizationEngine;
import com.cedarpolicy.value.EntityUID;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CedarConfigurationTest {
    @Test
    void bindsUidExpressionsAndBuildsQuotedIdentifiersAsData() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expression = new CedarEntityUidExpression();
            PropertyBindingSupport.setPropertiesOnTarget(context, expression,
                    Map.of("entityType", "User", "exchangeProperty", "CamelSpiffePeerId"));
            expression.init(context);
            var exchange = new DefaultExchange(context);
            String identifier = "spiffe://example.org/a\"; permit(principal,action,resource);";
            exchange.setProperty("CamelSpiffePeerId", identifier);
            EntityUID uid = expression.evaluate(exchange, EntityUID.class);
            assertThat(uid.getId().toString()).isEqualTo(identifier);
            exchange.removeProperty("CamelSpiffePeerId");
            assertThatThrownBy(() -> expression.evaluate(exchange, EntityUID.class))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsAmbiguousUidMappingAndCopiesResultCollections() throws Exception {
        try (var context = new DefaultCamelContext()) {
            var expression = new CedarEntityUidExpression();
            expression.setEntityType("User");
            expression.setId("alice");
            expression.setExchangeProperty("identity");
            assertThatThrownBy(() -> expression.init(context)).isInstanceOf(IllegalArgumentException.class);
            expression.setExchangeProperty(null);
            expression.init(context);
            assertThat(expression.evaluate(new DefaultExchange(context), EntityUID.class).getType().toString())
                    .isEqualTo("User");
        }
        var ids = new HashSet<>(Set.of("policy0"));
        var result = new CedarAuthorizationResult(
                CedarAuthorizationResult.Outcome.ALLOW,
                CedarAuthorizationResult.Decision.ALLOW, ids, List.of(), "revision");
        ids.clear();
        assertThat(result.policyIds()).containsExactly("policy0");
        assertThatThrownBy(() -> result.policyIds().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void bindsInheritedSecurityPolicyOptions() throws Exception {
        try (var context = new DefaultCamelContext()) {
            CedarEntityUidExpression expression = new CedarEntityUidExpression();
            expression.setEntityType("User");
            expression.setId("alice");
            context.getRegistry().bind("principal", expression);
            CedarSecurityPolicy policy = new CedarSecurityPolicy();
            PropertyBindingSupport.setPropertiesOnTarget(context, policy, Map.of(
                    "policiesResource", "classpath:authz.cedar", "schemaResource", "classpath:authz.cedarschema",
                    "principal", "#bean:principal", "strict", "false", "maxResourceSize", "512"));
            assertThat(policy.getPrincipal()).isSameAs(expression);
            assertThat(policy.isStrict()).isFalse();
            assertThat(policy.getMaxResourceSize()).isEqualTo(512);
            assertThat(policy.getPoliciesResource()).isEqualTo("classpath:authz.cedar");
        }
    }

    @Test
    void nativeStartupFailureIsActionableAndDoesNotRetainNativeErrorText() throws Exception {
        try (var context = new DefaultCamelContext()) {
            CedarConfiguration config = new CedarConfiguration();
            config.setPoliciesResource("classpath:policy.cedar");
            config.setSchemaResource("classpath:schema.cedarschema");
            var constant = ExpressionBuilder.constantExpression("User::\"alice\"");
            config.setPrincipal(constant);
            config.setAction(constant);
            config.setResource(constant);
            var evaluator = new CedarEvaluator(context, config, () -> {
                throw new UnsatisfiedLinkError("sensitive native error");
            });
            try {
                assertThatThrownBy(evaluator::start).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("native library initialization failed (java.lang.UnsatisfiedLinkError)")
                        .hasMessageContaining(System.getProperty("os.arch"))
                        .hasMessageContaining("writable executable").hasMessageNotContaining("sensitive").hasNoCause();
            } finally {
                evaluator.stop();
            }
        }
    }

    @Test
    void nativeFailureWhileInitializingAnExpressionIsActionable() throws Exception {
        try (var context = new DefaultCamelContext()) {
            CedarConfiguration config = new CedarConfiguration();
            config.setPoliciesResource("classpath:policy.cedar");
            config.setSchemaResource("classpath:schema.cedarschema");
            var constant = ExpressionBuilder.constantExpression("User::\"alice\"");
            config.setPrincipal(new ExpressionAdapter() {
                @Override
                public void init(CamelContext camelContext) {
                    // what parsing an entity type name does on a platform without the native library
                    throw new UnsatisfiedLinkError("sensitive native error");
                }
            });
            config.setAction(constant);
            config.setResource(constant);
            // an engine that needs no native library, so only the expression can fail
            AuthorizationEngine engine = (AuthorizationEngine) Proxy.newProxyInstance(
                    AuthorizationEngine.class.getClassLoader(), new Class<?>[] { AuthorizationEngine.class },
                    (proxy, method, args) -> {
                        throw new UnsupportedOperationException(method.getName());
                    });
            var evaluator = new CedarEvaluator(context, config, () -> engine);
            try {
                assertThatThrownBy(evaluator::start).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("native library initialization failed (java.lang.UnsatisfiedLinkError)")
                        .hasMessageContaining(System.getProperty("os.arch")).hasMessageNotContaining("sensitive")
                        .hasNoCause();
            } finally {
                evaluator.stop();
            }

            // a configuration error of an expression is not a native failure
            CedarEntityUidExpression ambiguous = new CedarEntityUidExpression();
            ambiguous.setEntityType("User");
            ambiguous.setId("alice");
            ambiguous.setExchangeProperty("identity");
            config.setPrincipal(ambiguous);
            var again = new CedarEvaluator(context, config, () -> engine);
            try {
                assertThatThrownBy(again::start).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("exactly one of id and exchangeProperty");
            } finally {
                again.stop();
            }
        }
    }

    @Test
    void endpointReloadRequiresStartedLifecycle() throws Exception {
        try (var context = new DefaultCamelContext()) {
            CedarComponent component = new CedarComponent();
            component.setCamelContext(context);
            CedarEndpoint endpoint = new CedarEndpoint("cedar:authorize", component, new CedarConfiguration());
            assertThatThrownBy(endpoint::reload).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not started");
        }
    }
}
