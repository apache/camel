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
package org.apache.camel.component.langchain4j.agent.integration;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LangChain4jAgentAutoConversionIT extends CamelTestSupport {

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {

        Agent mockAgent = (body, exchange) -> "Processed";

        context.getRegistry().bind("mockAgent", mockAgent);

        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("langchain4j-agent:test?agent=#mockAgent")
                        .to("mock:result");
            }
        };

    }

    @Test
    void shouldAutoConvertPlainString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello world");

        mock.assertIsSatisfied();

        String response = mock.getExchanges().get(0)
                .getMessage()
                .getMandatoryBody(String.class);

        assertNotNull(response);
    }

    @Test
    void shouldAutoConvertInputStream() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        try {
            template.sendBody("direct:start",
                    new ByteArrayInputStream("Hello stream".getBytes()));
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

        mock.assertIsSatisfied();
    }

    @Test
    void shouldConvertWrappedFile() throws Exception {
        Exchange exchange = context.getEndpoint("direct:test").createExchange();

        File file = File.createTempFile("camel", ".txt");
        Files.writeString(file.toPath(), "Hello file");

        GenericFile<File> genericFile = new GenericFile<>();
        genericFile.setFile(file);
        genericFile.setFileName(file.getName());

        AiAgentBody<?> body = context.getTypeConverter()
                .convertTo(AiAgentBody.class, exchange, genericFile);

        assertNotNull(body);
    }

    @Test
    void shouldConvertByteArray() {
        Exchange exchange = context.getEndpoint("direct:test").createExchange();

        exchange.getMessage().setHeader(
                "CamelLangChain4jAgentMediaType",
                "text/plain");

        AiAgentBody<?> body = context.getTypeConverter()
                .convertTo(AiAgentBody.class, exchange, "Hello".getBytes());

        assertNotNull(body);
    }

    @Test
    void shouldConvertInputStream() {
        Exchange exchange = context.getEndpoint("direct:test").createExchange();

        exchange.getMessage().setHeader(
                "CamelLangChain4jAgentMediaType",
                "text/plain");

        ByteArrayInputStream stream = new ByteArrayInputStream("Hello".getBytes());

        AiAgentBody<?> body = context.getTypeConverter()
                .convertTo(AiAgentBody.class, exchange, stream);

        assertNotNull(body);
    }

    @Test
    void shouldFailForUnsupportedMimeType() {
        Exchange exchange = context.getEndpoint("direct:test").createExchange();

        exchange.getMessage().setHeader(
                "CamelLangChain4jAgentMediaType",
                "application/zip");

        assertThrows(
                IllegalArgumentException.class,
                () -> context.getTypeConverter()
                        .convertTo(AiAgentBody.class, exchange, "data".getBytes()));
    }

}
