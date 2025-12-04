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

package org.apache.camel.component.langchain4j.tokenizer;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.tokenizer.config.LangChain4JQwenConfiguration;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.tokenizer.LangChain4jTokenizerDefinition;
import org.junit.jupiter.api.Test;

public class LangChain4JParagraphTokenizerConfigTest extends LangChain4JTokenizerTestSupport {

    @Test
    public void testTokenizer() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");

        mock.expectedMessageCount(4);

        template.sendBody("direct:start", TEXT);

        mock.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                final LangChain4JQwenConfiguration langChain4JConfiguration = new LangChain4JQwenConfiguration();

                langChain4JConfiguration.setMaxTokens(500);
                langChain4JConfiguration.setMaxOverlap(50);
                langChain4JConfiguration.setType(LangChain4jTokenizerDefinition.TokenizerType.QWEN.name());
                langChain4JConfiguration.setModelName("something");
                langChain4JConfiguration.setApiKey("my-api-key");

                from("direct:start")
                        .tokenize(tokenizer()
                                .byParagraph()
                                .configuration(langChain4JConfiguration)
                                .end())
                        .split()
                        .body()
                        .to("mock:result");
            }
        };
    }
}
