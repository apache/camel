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
package org.apache.camel.component.opa;

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks what the producer actually registers in the health registry.
 * <p/>
 * {@link OpaProducerHealthCheckTest} covers the check in isolation; this one goes through the route so that the id the
 * producer hands it is covered too, which is where the token used to leak from.
 */
public class OpaHealthCheckRegistrationTest extends CamelTestSupport {

    private static final String TOKEN = "s3cr3t-token";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:primary")
                        .to("opa:authz/allow?serverUrl=http://opa-primary:8181&bearerToken=" + TOKEN);
                from("direct:secondary")
                        .to("opa:authz/allow?serverUrl=http://opa-secondary:8181");
            }
        };
    }

    private List<HealthCheck> registeredChecks() {
        WritableHealthCheckRepository repository = HealthCheckHelper.getHealthCheckRepository(
                context, "producers", WritableHealthCheckRepository.class);
        assertThat(repository).isNotNull();
        // producer health checks are disabled globally by default, so enable the repository to read them back
        repository.setEnabled(true);
        return repository.stream().toList();
    }

    @Test
    void neverPublishesTheBearerTokenInAnyRegisteredId() {
        assertThat(registeredChecks())
                .isNotEmpty()
                .allSatisfy(check -> assertThat(check.getId()).doesNotContain(TOKEN));
    }

    @Test
    void keepsEndpointsOnDifferentServersDistinct() {
        List<String> ids = registeredChecks().stream().map(HealthCheck::getId).toList();

        assertThat(ids).hasSize(2).doesNotHaveDuplicates();
        assertThat(ids).anySatisfy(id -> assertThat(id).contains("opa-primary"));
        assertThat(ids).anySatisfy(id -> assertThat(id).contains("opa-secondary"));
    }
}
