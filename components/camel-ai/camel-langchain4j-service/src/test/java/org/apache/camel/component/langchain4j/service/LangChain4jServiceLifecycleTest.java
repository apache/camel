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
package org.apache.camel.component.langchain4j.service;

import dev.langchain4j.service.UserMessage;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.service.support.MockAiServices;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Producer and endpoint lifecycle: what happens around the resolution the producer does when it starts, rather than the
 * resolution itself.
 */
class LangChain4jServiceLifecycleTest extends LangChain4jServiceTestSupport {

    public interface Assistant {
        String chat(@UserMessage String text);
    }

    @Test
    void consumerIsRejected() throws Exception {
        context.getRegistry().bind("assistant", MockAiServices.create(Assistant.class));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("langchain4j-service:assistant").to("mock:out");
            }
        });

        assertThatThrownBy(() -> context.start())
                .as("the component is producer-only, so building a consumer from it must fail rather than start a "
                    + "route that can never receive anything")
                .rootCause()
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Consumer not supported");
    }

    @Test
    void restartingTheProducerResolvesTheServiceAgain() throws Exception {
        context.getRegistry().bind("assistant", MockAiServices.create(Assistant.class));
        context.start();

        LangChain4jServiceEndpoint endpoint
                = context.getEndpoint("langchain4j-service:assistant", LangChain4jServiceEndpoint.class);
        Producer producer = endpoint.createProducer();

        ServiceHelper.startService(producer);
        assertThat(invoke(endpoint, producer))
                .as("a started producer has resolved the service and bound its parameters")
                .isEqualTo("response from chat");

        ServiceHelper.stopService(producer);
        ServiceHelper.startService(producer);

        assertThat(invoke(endpoint, producer))
                .as("a producer that was stopped and started again must be able to invoke, so starting twice must not "
                    + "leave it in a state it cannot work from")
                .isEqualTo("response from chat");

        // Taking the bean away is what shows the lookup really runs again, rather than the producer reusing what it
        // resolved the first time
        ServiceHelper.stopService(producer);
        context.getRegistry().unbind("assistant");

        assertThatThrownBy(() -> ServiceHelper.startService(producer))
                .as("every start resolves the service afresh, so a producer started again once the bean is gone must "
                    + "fail rather than keep invoking the instance it resolved before")
                .isInstanceOf(NoSuchBeanException.class);
    }

    private String invoke(LangChain4jServiceEndpoint endpoint, Producer producer) throws Exception {
        Exchange exchange = endpoint.createExchange();
        exchange.getMessage().setBody("Hello");
        producer.process(exchange);
        return exchange.getMessage().getBody(String.class);
    }
}
