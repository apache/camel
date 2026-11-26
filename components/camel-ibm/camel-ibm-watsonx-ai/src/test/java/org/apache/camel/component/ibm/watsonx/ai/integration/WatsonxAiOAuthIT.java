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
package org.apache.camel.component.ibm.watsonx.ai.integration;

import java.util.concurrent.atomic.AtomicInteger;

import com.ibm.watsonx.ai.core.auth.ibmcloud.IBMCloudAuthenticator;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.watsonx.ai.TestOAuthClientAuthenticationFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The oauthProfile option against watsonx.ai: the identity provider of the test gives an IBM Cloud IAM access token
 * (obtained with the API key of the test), which the endpoint, configured without an API key, must send to watsonx.ai
 * as a Bearer token. Before CAMEL-XXXXX the endpoint posted this token to IAM as if it were an API key, which IAM
 * rejects.
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.apiKey", matches = ".+",
                                 disabledReason = "IBM watsonx.ai API Key not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.projectId", matches = ".+",
                                 disabledReason = "IBM watsonx.ai Project ID not provided")
})
public class WatsonxAiOAuthIT extends WatsonxAiTestSupport {

    private static final AtomicInteger LOOKUPS = new AtomicInteger();

    @BeforeAll
    static void identityProvider() {
        IBMCloudAuthenticator iam = IBMCloudAuthenticator.builder().apiKey(apiKey).build();
        LOOKUPS.set(0);
        TestOAuthClientAuthenticationFactory.tokens(profile -> {
            LOOKUPS.incrementAndGet();
            return iam.token();
        });
    }

    @AfterAll
    static void resetIdentityProvider() {
        TestOAuthClientAuthenticationFactory.reset();
        LOOKUPS.set(0);
    }

    @Test
    void chatWithOAuthProfile() {
        Exchange result = template.send("direct:chat", e -> e.getIn().setBody("What is the capital of France? One word."));

        assertNull(result.getException());
        String answer = result.getMessage().getBody(String.class);
        assertFalse(answer == null || answer.isBlank());
        // once at startup, once for the request
        assertEquals(2, LOOKUPS.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:chat")
                        .to("ibm-watsonx-ai:oauth?oauthProfile=ibm&baseUrl=" + baseUrl + "&projectId=" + projectId
                            + "&modelId=ibm/granite-4-h-small&operation=chat");
            }
        };
    }
}
