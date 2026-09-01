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
package org.apache.camel.component.openai;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chat-completion producer must fail with a clear error when no model is configured, matching the other producers,
 * instead of passing null to the OpenAI client and surfacing an opaque NullPointerException.
 */
public class OpenAIChatCompletionMissingModelTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // No model on the endpoint and no model header; a fake baseUrl is enough because the guard
                // rejects the exchange before any request is sent.
                from("direct:chat-no-model")
                        .to("openai:chat-completion?apiKey=dummy&baseUrl=http://localhost:1/v1");
            }
        };
    }

    @Test
    void missingModelReportsIllegalArgumentException() {
        Exchange result = template.request("direct:chat-no-model", e -> e.getIn().setBody("Hello"));
        assertThat(result.getException())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Model must be specified");
    }
}
