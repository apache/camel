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
package org.apache.camel.component.aws2.sqs;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.impl.health.DefaultHealthCheckRegistry;
import org.apache.camel.test.infra.aws.common.services.AWSService;
import org.apache.camel.test.infra.aws2.clients.AWSSDKClientUtils;
import org.apache.camel.test.infra.aws2.services.AWSServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

public class Sqs2ConsumerHealthCheckStaticCredsTest extends CamelTestSupport {

    @RegisterExtension
    public static AWSService service = AWSServiceFactory.createSQSService();

    private static final Logger LOG = LoggerFactory.getLogger(Sqs2ConsumerHealthCheckStaticCredsTest.class);

    CamelContext context;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        context = super.createCamelContext();
        context.getPropertiesComponent().setLocation("ref:prop");
        Sqs2Component component = new Sqs2Component(context);
        component.getConfiguration().setAmazonSQSClient(AWSSDKClientUtils.newSQSClient());
        component.init();
        context.addComponent("aws2-sqs", component);

        // install health check manually (yes a bit cumbersome)
        HealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        registry.setCamelContext(context);
        Object hc = registry.resolveById("context");
        registry.register(hc);
        hc = registry.resolveById("routes");
        registry.register(hc);
        hc = registry.resolveById("consumers");
        registry.register(hc);
        context.setExtension(HealthCheckRegistry.class, registry);

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("aws2-sqs://queue1?region=l&secretKey=l&accessKey=k&autoCreateQueue=true")
                        .startupOrder(2).log("${body}").routeId("test-health-it");
            }
        };
    }

    @Test
    public void testConnectivity() {

        Collection<HealthCheck.Result> res = HealthCheckHelper.invokeLiveness(context);
        boolean up = res.stream().allMatch(r -> r.getState().equals(HealthCheck.State.UP));
        Assertions.assertTrue(up, "liveness check");

        // health-check readiness should be down
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Collection<HealthCheck.Result> res2 = HealthCheckHelper.invokeReadiness(context);
            boolean down = res2.stream().allMatch(r -> r.getState().equals(HealthCheck.State.DOWN));
            boolean containsAws2SqsHealthCheck = res2.stream()
                    .filter(result -> result.getCheck().getId().startsWith("aws2-sqs-consumer"))
                    .findAny()
                    .isPresent();
            boolean hasRegionMessage = res2.stream()
                    .anyMatch(r -> r.getMessage().stream().anyMatch(msg -> msg.contains("region")));
            Assertions.assertTrue(down, "liveness check");
            Assertions.assertTrue(containsAws2SqsHealthCheck, "aws2-sqs check");
            Assertions.assertTrue(hasRegionMessage, "aws2-sqs check error message");
        });

    }
}
