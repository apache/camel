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
package org.apache.camel.dsl.yaml;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.LoadBalanceDefinition;
import org.apache.camel.model.loadbalancer.FailoverLoadBalancerDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoadBalanceTest extends YamlTestSupport {

    @Test
    void loadBalance() throws Exception {
        loadRoutes("""
                - from:
                   uri: "direct:start"
                   steps:
                     - loadBalance:
                         weightedLoadBalancer:
                           distributionRatio: "2,1"
                           roundRobin: false
                         steps:
                           - to: "mock:x"
                           - to: "mock:y"
                """);

        withMock("mock:x", mock -> mock.expectedMessageCount(2));
        withMock("mock:y", mock -> mock.expectedMessageCount(1));

        context.start();

        withTemplate(t -> {
            t.to("direct:start").withBody("hello").send();
            t.to("direct:start").withBody("hello").send();
            t.to("direct:start").withBody("hello").send();
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void failoverLoadBalancerInheritErrorHandlerPlaceholder() throws Exception {
        // YamlTestSupport validates against the raw schema (without the placeholder leniency of YamlValidator),
        // which types inheritErrorHandler as boolean; the runtime resolves the placeholder when the route starts
        // and YamlValidatorSchemaGroupsTest covers that the validator accepts it (CAMEL-24696)
        loadRoutesNoValidate("""
                - from:
                   uri: "direct:start"
                   steps:
                     - loadBalance:
                         failoverLoadBalancer:
                           inheritErrorHandler: "{{myInherit}}"
                           maximumFailoverAttempts: "3"
                         steps:
                           - to: "mock:x"
                           - to: "mock:y"
                """);

        var loadBalance = (LoadBalanceDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(loadBalance.getLoadBalancerType()).isInstanceOf(FailoverLoadBalancerDefinition.class);
        FailoverLoadBalancerDefinition failover = (FailoverLoadBalancerDefinition) loadBalance.getLoadBalancerType();
        assertThat(failover.getInheritErrorHandler()).isEqualTo("{{myInherit}}");
        assertThat(failover.getMaximumFailoverAttempts()).isEqualTo("3");
    }
}
