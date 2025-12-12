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
package org.apache.camel.component.aws2.polly.integration;

import java.io.InputStream;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.polly.Polly2Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.services.polly.model.DescribeVoicesRequest;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.TextType;
import software.amazon.awssdk.services.polly.model.Voice;
import software.amazon.awssdk.services.polly.model.VoiceId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Must be manually tested. Provide your own accessKey and secretKey using -Daws.access.key and -Daws.secret.key
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "aws.access.key", matches = ".*", disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "aws.secret.key", matches = ".*", disabledReason = "Secret key not provided")
})
public class Polly2ProducerManualIT extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void synthesizeSpeechTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:synthesizeSpeech", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Hello, this is a test of Amazon Polly.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        assertNotNull(exchange.getIn().getBody(InputStream.class));
        assertNotNull(exchange.getIn().getHeader(Polly2Constants.CONTENT_TYPE));
        assertNotNull(exchange.getIn().getHeader(Polly2Constants.REQUEST_CHARACTERS));
    }

    @Test
    public void synthesizeSpeechPojoTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:synthesizeSpeechPojo", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(SynthesizeSpeechRequest.builder()
                        .voiceId(VoiceId.JOANNA)
                        .outputFormat(OutputFormat.MP3)
                        .textType(TextType.TEXT)
                        .text("Hello, this is a POJO test of Amazon Polly.")
                        .build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        assertNotNull(exchange.getIn().getBody(InputStream.class));
    }

    @Test
    public void describeVoicesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeVoices", new Processor() {
            @Override
            public void process(Exchange exchange) {
                // No headers needed - operation is set in endpoint
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<Voice> voices = exchange.getIn().getBody(List.class);
        assertNotNull(voices);
        assertFalse(voices.isEmpty());
    }

    @Test
    public void describeVoicesPojoTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeVoicesPojo", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(DescribeVoicesRequest.builder().build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<Voice> voices = exchange.getIn().getBody(List.class);
        assertNotNull(voices);
        assertFalse(voices.isEmpty());
    }

    @Test
    public void listLexiconsTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listLexicons", new Processor() {
            @Override
            public void process(Exchange exchange) {
                // No headers needed - operation is set in endpoint
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        @SuppressWarnings("unchecked")
        List<?> lexicons = exchange.getIn().getBody(List.class);
        assertNotNull(lexicons);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:synthesizeSpeech")
                        .to("aws2-polly://test?accessKey=RAW({{aws.access.key}})&secretKey=RAW({{aws.secret.key}})&region=us-east-1&operation=synthesizeSpeech&voiceId=JOANNA&outputFormat=MP3&textType=TEXT")
                        .to("mock:result");

                from("direct:synthesizeSpeechPojo")
                        .to("aws2-polly://test?accessKey=RAW({{aws.access.key}})&secretKey=RAW({{aws.secret.key}})&region=us-east-1&operation=synthesizeSpeech&pojoRequest=true")
                        .to("mock:result");

                from("direct:describeVoices")
                        .to("aws2-polly://test?accessKey=RAW({{aws.access.key}})&secretKey=RAW({{aws.secret.key}})&region=us-east-1&operation=describeVoices")
                        .to("mock:result");

                from("direct:describeVoicesPojo")
                        .to("aws2-polly://test?accessKey=RAW({{aws.access.key}})&secretKey=RAW({{aws.secret.key}})&region=us-east-1&operation=describeVoices&pojoRequest=true")
                        .to("mock:result");

                from("direct:listLexicons")
                        .to("aws2-polly://test?accessKey=RAW({{aws.access.key}})&secretKey=RAW({{aws.secret.key}})&region=us-east-1&operation=listLexicons")
                        .to("mock:result");
            }
        };
    }
}
