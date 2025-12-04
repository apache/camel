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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.tokenizer.LangChain4jTokenizerDefinition;
import org.junit.jupiter.api.Test;

public class LangChain4JLineTokenizerTest extends LangChain4JTokenizerTestSupport {
    @Test
    public void testTokenizer() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");

        mock.expectedMessageCount(20);

        template.sendBody("direct:start", TEXT);

        mock.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .tokenize(tokenizer()
                                .byLine()
                                .maxTokens(1024, "gpt-4o-mini")
                                .maxOverlap(10)
                                .using(LangChain4jTokenizerDefinition.TokenizerType.OPEN_AI)
                                .end())
                        .split()
                        .body()
                        .to("mock:result");
            }
        };
    }
}
