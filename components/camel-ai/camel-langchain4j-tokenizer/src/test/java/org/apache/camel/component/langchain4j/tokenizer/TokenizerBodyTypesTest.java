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

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.tokenizer.LangChain4jTokenizerDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression tests for the tokenizer EIP body handling.
 */
public class TokenizerBodyTypesTest extends LangChain4JTokenizerTestSupport {

    @Test
    public void nullBodyCompletesWithException() {
        // CAMEL-26301: a null body must not hang the exchange forever — the AsyncCallback must always be called.
        // The tokenizer throws on null body; TokenizerProcessor must catch it, set it on the exchange,
        // and signal the callback so the error handler can deal with it normally.
        Exchange exchange = template.send("direct:start", e -> e.getMessage().setBody(null));
        assertNotNull(exchange.getException(), "exchange must have an exception, not hang");
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .tokenize(tokenizer()
                                .byParagraph()
                                .maxSegmentSize(1024)
                                .maxOverlap(10)
                                .using(LangChain4jTokenizerDefinition.TokenizerType.OPEN_AI)
                                .end());
            }
        };
    }
}
