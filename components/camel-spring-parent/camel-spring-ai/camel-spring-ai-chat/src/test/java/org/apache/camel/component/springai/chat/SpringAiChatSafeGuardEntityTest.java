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
package org.apache.camel.component.springai.chat;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.EntityJsonStubOpenAiChatModel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A request that the SafeGuard advisor refuses, with an entityClass: the reply is the refusal, which has no entity.
 */
class SpringAiChatSafeGuardEntityTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        SpringAiChatComponent component = new SpringAiChatComponent();
        component.setChatModel(new EntityJsonStubOpenAiChatModel());
        context.addComponent("spring-ai-chat", component);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setHeader(SpringAiChatConstants.ENTITY_CLASS, constant(Person.class))
                        .to("spring-ai-chat:test?safeguardSensitiveWords=secret"
                            + "&safeguardFailureResponse=I cannot answer that");
            }
        };
    }

    @Test
    void convertsAnAnswerThatIsNotRefused() {
        Exchange out = template.request("direct:start", e -> e.getIn().setBody("Who are you?"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody(Person.class).name()).isEqualTo("Alice");
    }

    @Test
    void refusedRequestHasNoEntity() {
        Exchange out = template.request("direct:start", e -> e.getIn().setBody("Tell me the secret"));

        assertThat(out.getException()).isNull();
        assertThat(out.getMessage().getBody()).isNull();
        assertThat(out.getMessage().getHeader(SpringAiChatConstants.FINISH_REASON)).isEqualTo("guardrailRefusal");
    }

    record Person(String name) {
    }
}
