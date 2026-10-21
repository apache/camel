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
package org.apache.camel.dataformat.beanio;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The splitter decodes the bytes of a file or a stream with the charset of the exchange (such as the charset option of
 * the file consumer), else with its encoding, not with the default charset of the JVM.
 */
class BeanIOSplitterEncodingTest extends CamelTestSupport {

    private static final String DATA = "J\u00f6rg,M\u00fcller,D\u00e9veloppeur,75000,10012009" + Constants.LS
                                       + "Ren\u00e9e,Stra\u00dfe,Architect,80000,01152008" + Constants.LS;

    @TempDir
    Path dir;

    private static BeanIOSplitter splitter() throws Exception {
        BeanIOSplitter splitter = new BeanIOSplitter();
        splitter.setMapping("org/apache/camel/dataformat/beanio/mappings.xml");
        splitter.setStreamName("employeeFile");
        return splitter;
    }

    private static List<String> names(MockEndpoint mock) {
        return mock.getReceivedExchanges().stream()
                .map(e -> e.getIn().getBody(Employee.class))
                .map(e -> e.getFirstName() + " " + e.getLastName() + " " + e.getTitle())
                .toList();
    }

    @Test
    void fileIsReadWithTheEncodingOfTheSplitter() throws Exception {
        Path file = Files.writeString(dir.resolve("employees.csv"), DATA, StandardCharsets.ISO_8859_1);
        BeanIOSplitter splitter = splitter();
        splitter.setEncoding(StandardCharsets.ISO_8859_1);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:split").split(splitter).streaming().to("mock:split");
            }
        });
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(2);

        template.sendBody("direct:split", file.toFile());

        mock.assertIsSatisfied();
        assertEquals(List.of("J\u00f6rg M\u00fcller D\u00e9veloppeur", "Ren\u00e9e Stra\u00dfe Architect"), names(mock));
    }

    @Test
    void fileFromTheFileConsumerIsReadWithItsCharset() throws Exception {
        Files.writeString(dir.resolve("employees.csv"), DATA, StandardCharsets.ISO_8859_1);
        BeanIOSplitter splitter = splitter();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                fromF("file:%s?charset=ISO-8859-1&noop=true&initialDelay=0&delay=10", dir)
                        .split(splitter).streaming().to("mock:split");
            }
        });
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(2);

        mock.assertIsSatisfied();
        assertEquals(List.of("J\u00f6rg M\u00fcller D\u00e9veloppeur", "Ren\u00e9e Stra\u00dfe Architect"), names(mock));
    }

    @Test
    void bytesAreReadWithTheCharsetOfTheExchange() throws Exception {
        BeanIOSplitter splitter = splitter();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:split").split(splitter).streaming().to("mock:split");
            }
        });
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(2);

        template.send("direct:split", exchange -> {
            exchange.setProperty(Exchange.CHARSET_NAME, "ISO-8859-1");
            exchange.getIn().setBody(DATA.getBytes(StandardCharsets.ISO_8859_1));
        });

        mock.assertIsSatisfied();
        assertEquals(List.of("J\u00f6rg M\u00fcller D\u00e9veloppeur", "Ren\u00e9e Stra\u00dfe Architect"), names(mock));
    }

    @Test
    void bytesAreReadWithTheEncodingOfTheSplitter() throws Exception {
        BeanIOSplitter splitter = splitter();
        splitter.setEncoding(StandardCharsets.ISO_8859_1);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:split").split(splitter).streaming().to("mock:split");
            }
        });
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(2);

        template.sendBody("direct:split", DATA.getBytes(StandardCharsets.ISO_8859_1));

        mock.assertIsSatisfied();
        assertEquals(List.of("J\u00f6rg M\u00fcller D\u00e9veloppeur", "Ren\u00e9e Stra\u00dfe Architect"), names(mock));
    }

    @Test
    void textIsReadAsItIs() throws Exception {
        BeanIOSplitter splitter = splitter();
        // an encoding that differs from the default charset of the JVM must not apply to text
        splitter.setEncoding(StandardCharsets.UTF_16BE);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:split").split(splitter).streaming().to("mock:split");
            }
        });
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(2);

        template.sendBody("direct:split", new StringBuilder(DATA));

        mock.assertIsSatisfied();
        assertEquals(List.of("J\u00f6rg M\u00fcller D\u00e9veloppeur", "Ren\u00e9e Stra\u00dfe Architect"), names(mock));
    }
}
