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

import org.apache.camel.Producer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises generated endpoint configuration and registry references under the normal Maven component build. */
@EnabledOnOs(OS.LINUX)
@EnabledIfSystemProperty(named = "os.arch", matches = "amd64|x86_64")
@EnabledIf("glibcAvailable")
class CedarEndpointBindingTest {
    static boolean glibcAvailable() {
        return CedarEvaluatorTest.glibcAvailable();
    }

    @Test
    void bindsEndpointUriAndUsesConfiguredExpressions() throws Exception {
        try (var fixture = new CedarEvaluatorTest.Fixture(CedarEvaluatorTest.PERMIT, CedarEvaluatorTest.SCHEMA)) {
            fixture.context.addComponent("cedar", new CedarComponent());
            fixture.context.getRegistry().bind("principal", fixture.config.getPrincipal());
            fixture.context.getRegistry().bind("action", fixture.config.getAction());
            fixture.context.getRegistry().bind("resource", fixture.config.getResource());
            fixture.context.start();
            CedarEndpoint endpoint = fixture.context.getEndpoint("cedar:authorize?policiesResource="
                                                                 + fixture.config.getPoliciesResource() + "&schemaResource="
                                                                 + fixture.config.getSchemaResource()
                                                                 + "&principal=#bean:principal&action=#bean:action&resource=#bean:resource&strict=true",
                    CedarEndpoint.class);
            endpoint.start();
            Producer producer = endpoint.createProducer();
            producer.start();
            try {
                var exchange = fixture.exchange();
                Object body = new Object();
                exchange.getMessage().setBody(body);
                producer.process(exchange);
                assertThat(endpoint.getConfiguration().getPrincipal()).isSameAs(fixture.config.getPrincipal());
                assertThat(
                        exchange.getProperty(CedarConstants.AUTHORIZATION_RESULT, CedarAuthorizationResult.class).isAllowed())
                        .isTrue();
                assertThat(exchange.getMessage().getBody()).isSameAs(body);
            } finally {
                producer.stop();
            }
        }
    }
}
