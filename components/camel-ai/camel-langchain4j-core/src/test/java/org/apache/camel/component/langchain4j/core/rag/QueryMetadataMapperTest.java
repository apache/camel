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
package org.apache.camel.component.langchain4j.core.rag;

import java.util.List;
import java.util.Map;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.rag.query.Metadata;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.langchain4j.core.rag.CamelContentRetrieverConstants.QUERY_MEMORY_ID;
import static org.apache.camel.component.langchain4j.core.rag.CamelContentRetrieverConstants.QUERY_PARAMS;
import static org.apache.camel.component.langchain4j.core.rag.CamelContentRetrieverConstants.QUERY_USER_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class QueryMetadataMapperTest extends CamelTestSupport {

    private Exchange mapped(Metadata metadata) {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        QueryMetadataMapper.toExchange(metadata, exchange);
        return exchange;
    }

    private static Metadata metadata(ChatMessage chatMessage) {
        return Metadata.builder()
                .chatMessage(chatMessage)
                .invocationContext(InvocationContext.builder().build())
                .build();
    }

    @Test
    public void nullMetadataSetsNothing() {
        assertThat(mapped(null).getProperties()).isEmpty();
    }

    @Test
    public void metadataWithoutMappedValuesSetsNothing() {
        assertThat(mapped(metadata(AiMessage.from("answer"))).getProperties()).isEmpty();
    }

    @Test
    public void singleTextUserMessageIsMapped() {
        Exchange exchange = mapped(metadata(UserMessage.from("original")));

        assertThat(exchange.getProperty(QUERY_USER_MESSAGE, String.class)).isEqualTo("original");
    }

    @Test
    public void multimodalUserMessageIsNotMapped() {
        UserMessage multimodal = UserMessage.from(
                List.of(TextContent.from("original"), ImageContent.from("aW1hZ2U=")));

        assertThat(mapped(metadata(multimodal)).getProperty(QUERY_USER_MESSAGE, String.class)).isNull();
    }

    @Test
    public void nonUserMessagesAreNotMapped() {
        assertThat(mapped(metadata(AiMessage.from("answer"))).getProperty(QUERY_USER_MESSAGE, String.class)).isNull();
        assertThat(mapped(metadata(SystemMessage.from("system"))).getProperty(QUERY_USER_MESSAGE, String.class))
                .isNull();
    }

    @Test
    public void chatMemoryIdIsMapped() {
        Metadata metadata = Metadata.builder()
                .chatMessage(UserMessage.from("q"))
                .invocationContext(InvocationContext.builder().chatMemoryId("mem-1").build())
                .build();

        assertThat(mapped(metadata).getProperty(QUERY_MEMORY_ID, String.class)).isEqualTo("mem-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void invocationParametersAreMapped() {
        InvocationParameters parameters = new InvocationParameters();
        parameters.put("customerId", "c-42");
        parameters.put("locale", "fr");
        Metadata metadata = Metadata.builder()
                .chatMessage(UserMessage.from("q"))
                .invocationContext(InvocationContext.builder().invocationParameters(parameters).build())
                .build();

        Exchange exchange = mapped(metadata);

        Map<String, Object> params = (Map<String, Object>) exchange.getProperty(QUERY_PARAMS, Map.class);
        assertThat(params).containsOnly(Map.entry("customerId", "c-42"), Map.entry("locale", "fr"));
        assertThatThrownBy(() -> params.put("customerId", "other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void queryWithoutInvocationContextSetsNoParameters() {
        Exchange exchange = mapped(metadata(UserMessage.from("q")));

        assertThat(exchange.getProperty(QUERY_PARAMS)).isNull();
    }
}
