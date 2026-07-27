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
package org.apache.camel.component.aws2.eventbridge.localstack;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.component.aws2.eventbridge.EventbridgeComponent;
import org.apache.camel.test.infra.aws.common.services.AWSService;
import org.apache.camel.test.infra.aws2.clients.AWSSDKClientUtils;
import org.apache.camel.test.infra.aws2.services.AWSServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.DeleteRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.ListRulesRequest;
import software.amazon.awssdk.services.eventbridge.model.ListRulesResponse;
import software.amazon.awssdk.services.eventbridge.model.ListTargetsByRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.ListTargetsByRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.PutRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.RemoveTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.Target;

import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Aws2EventbridgeBase extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(Aws2EventbridgeBase.class);
    private static final String DEFAULT_EVENT_BUS = "default";

    @RegisterExtension
    public static AWSService service = AWSServiceFactory.createSingletonEventBridgeService();

    protected EventBridgeClient eventBridgeClient;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        eventBridgeClient = AWSSDKClientUtils.newEventBridgeClient();
        EventbridgeComponent eventbridgeComponent = context.getComponent("aws2-eventbridge", EventbridgeComponent.class);
        eventbridgeComponent.getConfiguration().setEventbridgeClient(eventBridgeClient);
        return context;
    }

    /**
     * Waits for the EventBridge service to be fully ready for both read and write operations (the container health
     * check may pass before all services are available), then cleans up all rules on the default event bus. This
     * prevents test interference when using a singleton container (floci/LocalStack) shared across test classes —
     * leftover rules from a prior test could cause assertions on rule counts or rule state to fail.
     */
    @BeforeEach
    void waitForServiceAndCleanUp() {
        if (eventBridgeClient == null) {
            return;
        }

        // Wait for the EventBridge service to accept write requests — the container health
        // check may return 200, and even listRules may succeed, before write operations like
        // putRule are ready. We probe with a real putRule + deleteRule cycle to confirm the
        // service is fully initialized.
        try {
            await().atMost(30, TimeUnit.SECONDS)
                    .pollInterval(2, TimeUnit.SECONDS)
                    .ignoreExceptions()
                    .until(() -> {
                        eventBridgeClient.putRule(PutRuleRequest.builder()
                                .name("__readiness_probe__")
                                .eventBusName(DEFAULT_EVENT_BUS)
                                .eventPattern("{\"source\":[\"readiness-probe\"]}")
                                .build());
                        eventBridgeClient.deleteRule(DeleteRuleRequest.builder()
                                .name("__readiness_probe__")
                                .eventBusName(DEFAULT_EVENT_BUS)
                                .build());
                        return true;
                    });
        } catch (Exception e) {
            LOG.warn("EventBridge service not ready for writes after 30s, proceeding anyway: {}", e.getMessage());
        }

        // Clean up any leftover rules from previous test classes
        try {
            ListRulesResponse listResponse = eventBridgeClient.listRules(
                    ListRulesRequest.builder().eventBusName(DEFAULT_EVENT_BUS).build());
            if (listResponse != null && listResponse.hasRules()) {
                for (var rule : listResponse.rules()) {
                    removeTargetsAndDeleteRule(rule.name());
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to clean up rules: {}", e.getMessage());
        }
    }

    private void removeTargetsAndDeleteRule(String ruleName) {
        try {
            ListTargetsByRuleResponse targets = eventBridgeClient.listTargetsByRule(
                    ListTargetsByRuleRequest.builder()
                            .rule(ruleName)
                            .eventBusName(DEFAULT_EVENT_BUS)
                            .build());
            if (targets.hasTargets() && !targets.targets().isEmpty()) {
                eventBridgeClient.removeTargets(
                        RemoveTargetsRequest.builder()
                                .rule(ruleName)
                                .ids(targets.targets().stream().map(Target::id).toList())
                                .eventBusName(DEFAULT_EVENT_BUS)
                                .build());
            }
        } catch (Exception e) {
            LOG.warn("Failed to remove targets for rule {}: {}", ruleName, e.getMessage());
        }
        try {
            eventBridgeClient.deleteRule(
                    DeleteRuleRequest.builder()
                            .name(ruleName)
                            .eventBusName(DEFAULT_EVENT_BUS)
                            .build());
        } catch (Exception e) {
            LOG.warn("Failed to delete rule {}: {}", ruleName, e.getMessage());
        }
    }
}
