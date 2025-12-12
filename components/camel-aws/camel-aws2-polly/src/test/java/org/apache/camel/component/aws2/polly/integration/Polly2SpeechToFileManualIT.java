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

import java.io.File;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test that synthesizes speech and saves the audio file to the target directory. Must be manually tested.
 * Provide your own accessKey and secretKey using -Daws.access.key and -Daws.secret.key
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "aws.access.key", matches = ".*", disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "aws.secret.key", matches = ".*", disabledReason = "Secret key not provided")
})
public class Polly2SpeechToFileManualIT extends CamelTestSupport {

    private static final String OUTPUT_DIR = "target/polly-output";

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void synthesizeSpeechToMp3FileTest() throws Exception {
        mock.expectedMessageCount(1);

        template.send("direct:synthesizeToFileMp3", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Exchange.FILE_NAME, "polly-test-joanna.mp3");
                exchange.getIn().setBody("Hello, this is a test of Amazon Polly. The audio is saved to a file.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        File outputFile = new File(OUTPUT_DIR + "/polly-test-joanna.mp3");
        assertTrue(outputFile.exists(), "Output file should exist");
        assertTrue(outputFile.length() > 0, "Output file should not be empty");
    }

    @Test
    public void synthesizeSpeechWithNeuralEngineToFileTest() throws Exception {
        mock.expectedMessageCount(1);

        template.send("direct:synthesizeToFileNeural", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Exchange.FILE_NAME, "polly-test-joanna-neural.mp3");
                exchange.getIn().setBody("Hello, this is a test using the neural engine. It sounds more natural.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        File outputFile = new File(OUTPUT_DIR + "/polly-test-joanna-neural.mp3");
        assertTrue(outputFile.exists(), "Output file should exist");
        assertTrue(outputFile.length() > 0, "Output file should not be empty");
    }

    @Test
    public void synthesizeSpeechWithSsmlToFileTest() throws Exception {
        mock.expectedMessageCount(1);

        template.send("direct:synthesizeToFileSsml", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Exchange.FILE_NAME, "polly-test-ssml.mp3");
                exchange.getIn().setBody(
                        "<speak>Hello! <break time=\"500ms\"/> This is a test with <emphasis level=\"strong\">SSML</emphasis> markup.</speak>");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        File outputFile = new File(OUTPUT_DIR + "/polly-test-ssml.mp3");
        assertTrue(outputFile.exists(), "Output file should exist");
        assertTrue(outputFile.length() > 0, "Output file should not be empty");
    }

    @Test
    public void synthesizeSpeechToOggFileTest() throws Exception {
        mock.expectedMessageCount(1);

        template.send("direct:synthesizeToFileOgg", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Exchange.FILE_NAME, "polly-test-matthew.ogg");
                exchange.getIn().setBody("Hello, this is Matthew speaking. The audio is in OGG format.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        File outputFile = new File(OUTPUT_DIR + "/polly-test-matthew.ogg");
        assertTrue(outputFile.exists(), "Output file should exist");
        assertTrue(outputFile.length() > 0, "Output file should not be empty");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:synthesizeToFileMp3")
                        .to("aws2-polly://test?accessKey=RAW({{aws.access.key}})&secretKey=RAW({{aws.secret.key}})&region=us-east-1&operation=synthesizeSpeech&voiceId=JOANNA&outputFormat=MP3&textType=TEXT")
                        .to("file:" + OUTPUT_DIR)
                        .to("mock:result");

                from("direct:synthesizeToFileNeural")
                        .to("aws2-polly://test?accessKey=RAW({{aws.access.key}})&secretKey=RAW({{aws.secret.key}})&region=us-east-1&operation=synthesizeSpeech&voiceId=JOANNA&outputFormat=MP3&textType=TEXT&engine=NEURAL")
                        .to("file:" + OUTPUT_DIR)
                        .to("mock:result");

                from("direct:synthesizeToFileSsml")
                        .to("aws2-polly://test?accessKey=RAW({{aws.access.key}})&secretKey=RAW({{aws.secret.key}})&region=us-east-1&operation=synthesizeSpeech&voiceId=JOANNA&outputFormat=MP3&textType=SSML")
                        .to("file:" + OUTPUT_DIR)
                        .to("mock:result");

                from("direct:synthesizeToFileOgg")
                        .to("aws2-polly://test?accessKey=RAW({{aws.access.key}})&secretKey=RAW({{aws.secret.key}})&region=us-east-1&operation=synthesizeSpeech&voiceId=MATTHEW&outputFormat=OGG_VORBIS&textType=TEXT")
                        .to("file:" + OUTPUT_DIR)
                        .to("mock:result");
            }
        };
    }
}
