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
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.TokenizerDefinition;
import org.apache.camel.model.tokenizer.LangChain4jCharacterTokenizerDefinition;
import org.apache.camel.model.tokenizer.LangChain4jLineTokenizerDefinition;
import org.apache.camel.model.tokenizer.LangChain4jParagraphTokenizerDefinition;
import org.apache.camel.model.tokenizer.LangChain4jSentenceTokenizerDefinition;
import org.apache.camel.model.tokenizer.LangChain4jWordTokenizerDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CAMEL-25777: the XML and YAML DSL create the tokenizer with the no-arg constructors and set its implementation, but
 * not the tokenizer name that the Java DSL builder sets, so their routes failed to start. The route here is built the
 * same way, from the model (a component cannot depend on the YAML DSL).
 */
public class LangChain4JTokenizerDefinitionTest extends LangChain4JTokenizerTestSupport {

    @Test
    public void testTokenizerAsTheXmlAndYamlDslBuildIt() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(4);

        template.sendBody("direct:start", TEXT);

        mock.assertIsSatisfied();
    }

    @Test
    public void testEveryTokenizerHasItsName() {
        // the names of the tokenizer services, which the Java DSL builder gives them too
        assertEquals("langChain4jCharacterTokenizer", new LangChain4jCharacterTokenizerDefinition().tokenizerName());
        assertEquals("langChain4jLineTokenizer", new LangChain4jLineTokenizerDefinition().tokenizerName());
        assertEquals("langChain4jParagraphTokenizer", new LangChain4jParagraphTokenizerDefinition().tokenizerName());
        assertEquals("langChain4jSentenceTokenizer", new LangChain4jSentenceTokenizerDefinition().tokenizerName());
        assertEquals("langChain4jWordTokenizer", new LangChain4jWordTokenizerDefinition().tokenizerName());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // what the XML and YAML DSL parse <tokenizer><langChain4jParagraphTokenizer .../></tokenizer> into
                LangChain4jParagraphTokenizerDefinition paragraph = new LangChain4jParagraphTokenizerDefinition();
                paragraph.setMaxTokens("1024");
                paragraph.setMaxOverlap("10");
                paragraph.setTokenizerType("OPEN_AI");
                TokenizerDefinition tokenizer = new TokenizerDefinition();
                tokenizer.setTokenizerImplementation(paragraph);

                RouteDefinition route = from("direct:start");
                route.addOutput(tokenizer);
                route.split().body().to("mock:result");
            }
        };
    }
}
