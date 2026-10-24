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
package org.apache.camel.component.aws2.translate;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.translate.model.TranslateTextRequest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Translate2ProducerTest extends CamelTestSupport {

    @BindToRegistry("amazonTranslateClient")
    AmazonAWSTranslateMock clientMock = new AmazonAWSTranslateMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void translateTextTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:translateText", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Translate2Constants.SOURCE_LANGUAGE, Translate2LanguageEnum.ITALIAN);
                exchange.getIn().setHeader(Translate2Constants.TARGET_LANGUAGE, Translate2LanguageEnum.ENGLISH);
                exchange.getIn().setBody("ciao");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        String resultGet = exchange.getIn().getBody(String.class);
        assertEquals("Hello", resultGet);

    }

    @Test
    public void translateTextPojoTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:translatePojoText", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn()
                        .setBody(TranslateTextRequest.builder().sourceLanguageCode(Translate2LanguageEnum.ITALIAN.toString())
                                .targetLanguageCode(Translate2LanguageEnum.ENGLISH.toString()).text("ciao").build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        String resultGet = exchange.getIn().getBody(String.class);
        assertEquals("Hello", resultGet);

    }

    @Test
    void translateTextWithPojoRequestAndWrongBodyTypeThrows() {
        assertThatThrownBy(() -> template.requestBody("direct:translatePojoText", "not a TranslateTextRequest"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("translateText operation requires TranslateTextRequest in POJO mode");
    }

    @Test
    public void translateTextTestOptions() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:translateTextOptions", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("ciao");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        String resultGet = exchange.getIn().getBody(String.class);
        assertEquals("Hello", resultGet);

    }

    @Test
    public void sourceLanguageOptionAndTargetLanguageHeader() {
        template.send("direct:translateTextSourceOption", exchange -> {
            exchange.getIn().setHeader(Translate2Constants.TARGET_LANGUAGE, Translate2LanguageEnum.ENGLISH);
            exchange.getIn().setBody("ciao");
        });

        assertEquals("it", clientMock.lastRequest.sourceLanguageCode());
        assertEquals("en", clientMock.lastRequest.targetLanguageCode());
    }

    @Test
    public void sourceLanguageHeaderAndTargetLanguageOption() {
        template.send("direct:translateTextTargetOption", exchange -> {
            exchange.getIn().setHeader(Translate2Constants.SOURCE_LANGUAGE, Translate2LanguageEnum.ITALIAN);
            exchange.getIn().setBody("ciao");
        });

        assertEquals("it", clientMock.lastRequest.sourceLanguageCode());
        assertEquals("en", clientMock.lastRequest.targetLanguageCode());
    }

    @Test
    public void optionWinsOverHeader() {
        template.send("direct:translateTextOptions", exchange -> {
            exchange.getIn().setHeader(Translate2Constants.SOURCE_LANGUAGE, Translate2LanguageEnum.FRENCH);
            exchange.getIn().setHeader(Translate2Constants.TARGET_LANGUAGE, Translate2LanguageEnum.GERMAN);
            exchange.getIn().setBody("ciao");
        });

        assertEquals("it", clientMock.lastRequest.sourceLanguageCode());
        assertEquals("en", clientMock.lastRequest.targetLanguageCode());
    }

    @Test
    public void autodetectSourceLanguageWithTargetLanguageHeader() {
        template.send("direct:translateTextAutodetect", exchange -> {
            exchange.getIn().setHeader(Translate2Constants.SOURCE_LANGUAGE, Translate2LanguageEnum.FRENCH);
            exchange.getIn().setHeader(Translate2Constants.TARGET_LANGUAGE, Translate2LanguageEnum.ENGLISH);
            exchange.getIn().setBody("ciao");
        });

        assertEquals("auto", clientMock.lastRequest.sourceLanguageCode());
        assertEquals("en", clientMock.lastRequest.targetLanguageCode());
    }

    @Test
    void missingTargetLanguageThrows() {
        assertThatThrownBy(() -> template.requestBody("direct:translateTextSourceOption", "ciao"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage(
                        "The target language is missing: set the targetLanguage option or the CamelAwsTranslateTargetLanguage header");
    }

    @Test
    void missingTargetLanguageWithAutodetectThrows() {
        assertThatThrownBy(() -> template.requestBody("direct:translateTextAutodetect", "ciao"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage(
                        "The target language is missing: set the targetLanguage option or the CamelAwsTranslateTargetLanguage header");
    }

    @Test
    void missingSourceLanguageThrows() {
        assertThatThrownBy(() -> template.requestBody("direct:translateTextTargetOption", "ciao"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage(
                        "The source language is missing: set the sourceLanguage option or the CamelAwsTranslateSourceLanguage header,"
                                     + " or enable the autodetectSourceLanguage option");
    }

    @Test
    void croatianLanguageCode() {
        assertEquals("hr", Translate2LanguageEnum.CROATIAN.toString());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:translateText")
                        .to("aws2-translate://test?translateClient=#amazonTranslateClient&operation=translateText")
                        .to("mock:result");
                from("direct:translatePojoText").to(
                        "aws2-translate://test?translateClient=#amazonTranslateClient&operation=translateText&pojoRequest=true")
                        .to("mock:result");
                from("direct:translateTextOptions").to(
                        "aws2-translate://test?translateClient=#amazonTranslateClient&operation=translateText&sourceLanguage=it&targetLanguage=en")
                        .to("mock:result");
                from("direct:translateTextSourceOption").to(
                        "aws2-translate://test?translateClient=#amazonTranslateClient&operation=translateText&sourceLanguage=it");
                from("direct:translateTextTargetOption").to(
                        "aws2-translate://test?translateClient=#amazonTranslateClient&operation=translateText&targetLanguage=en");
                from("direct:translateTextAutodetect").to(
                        "aws2-translate://test?translateClient=#amazonTranslateClient&operation=translateText&autodetectSourceLanguage=true");
            }
        };
    }
}
