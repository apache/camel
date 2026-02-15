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
package org.apache.camel.component.aws2.polly;

import java.io.InputStream;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.polly.model.Lexicon;
import software.amazon.awssdk.services.polly.model.LexiconDescription;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.SynthesisTask;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.Voice;
import software.amazon.awssdk.services.polly.model.VoiceId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Polly2ProducerTest extends CamelTestSupport {

    @BindToRegistry("amazonPollyClient")
    AmazonPollyClientMock clientMock = new AmazonPollyClientMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void synthesizeSpeechTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:synthesizeSpeech", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Polly2Constants.VOICE_ID, VoiceId.JOANNA);
                exchange.getIn().setBody("Hello, this is a test.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        assertNotNull(exchange.getIn().getBody(InputStream.class));
        assertEquals("audio/mpeg", exchange.getIn().getHeader(Polly2Constants.CONTENT_TYPE));
    }

    @Test
    public void synthesizeSpeechWithOptionsTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:synthesizeSpeechWithOptions", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Hello, this is a test with neural engine.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        assertNotNull(exchange.getIn().getBody(InputStream.class));
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
                        .text("Hello from POJO request")
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
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        List<Voice> voices = exchange.getIn().getBody(List.class);
        assertNotNull(voices);
        assertEquals(2, voices.size());
    }

    @Test
    public void listLexiconsTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listLexicons", new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        List<LexiconDescription> lexicons = exchange.getIn().getBody(List.class);
        assertNotNull(lexicons);
        assertEquals(1, lexicons.size());
    }

    @Test
    public void getLexiconTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getLexicon", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Polly2Constants.LEXICON_NAME, "TestLexicon");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Lexicon lexicon = exchange.getIn().getBody(Lexicon.class);
        assertNotNull(lexicon);
        assertEquals("TestLexicon", lexicon.name());
    }

    @Test
    public void putLexiconTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:putLexicon", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Polly2Constants.LEXICON_NAME, "NewLexicon");
                exchange.getIn().setBody("<?xml version=\"1.0\" encoding=\"UTF-8\"?><lexicon></lexicon>");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        assertNotNull(exchange.getIn().getBody());
    }

    @Test
    public void deleteLexiconTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteLexicon", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Polly2Constants.LEXICON_NAME, "TestLexicon");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        assertNotNull(exchange.getIn().getBody());
    }

    @Test
    public void startSpeechSynthesisTaskTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:startSpeechSynthesisTask", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Polly2Constants.VOICE_ID, VoiceId.JOANNA);
                exchange.getIn().setHeader(Polly2Constants.S3_BUCKET, "my-bucket");
                exchange.getIn().setBody("This is a long text for async synthesis.");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        SynthesisTask task = exchange.getIn().getBody(SynthesisTask.class);
        assertNotNull(task);
        assertEquals("test-task-id-123", task.taskId());
        assertEquals("test-task-id-123", exchange.getIn().getHeader(Polly2Constants.TASK_ID));
    }

    @Test
    public void getSpeechSynthesisTaskTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getSpeechSynthesisTask", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Polly2Constants.TASK_ID, "test-task-id-123");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        SynthesisTask task = exchange.getIn().getBody(SynthesisTask.class);
        assertNotNull(task);
        assertEquals("test-task-id-123", task.taskId());
    }

    @Test
    public void listSpeechSynthesisTasksTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listSpeechSynthesisTasks", new Processor() {
            @Override
            public void process(Exchange exchange) {
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        List<SynthesisTask> tasks = exchange.getIn().getBody(List.class);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:synthesizeSpeech")
                        .to("aws2-polly://test?pollyClient=#amazonPollyClient&operation=synthesizeSpeech")
                        .to("mock:result");

                from("direct:synthesizeSpeechWithOptions")
                        .to("aws2-polly://test?pollyClient=#amazonPollyClient&operation=synthesizeSpeech&voiceId=JOANNA&engine=NEURAL&outputFormat=MP3")
                        .to("mock:result");

                from("direct:synthesizeSpeechPojo")
                        .to("aws2-polly://test?pollyClient=#amazonPollyClient&operation=synthesizeSpeech&pojoRequest=true")
                        .to("mock:result");

                from("direct:describeVoices")
                        .to("aws2-polly://test?pollyClient=#amazonPollyClient&operation=describeVoices")
                        .to("mock:result");

                from("direct:listLexicons")
                        .to("aws2-polly://test?pollyClient=#amazonPollyClient&operation=listLexicons")
                        .to("mock:result");

                from("direct:getLexicon")
                        .to("aws2-polly://test?pollyClient=#amazonPollyClient&operation=getLexicon")
                        .to("mock:result");

                from("direct:putLexicon")
                        .to("aws2-polly://test?pollyClient=#amazonPollyClient&operation=putLexicon")
                        .to("mock:result");

                from("direct:deleteLexicon")
                        .to("aws2-polly://test?pollyClient=#amazonPollyClient&operation=deleteLexicon")
                        .to("mock:result");

                from("direct:startSpeechSynthesisTask")
                        .to("aws2-polly://test?pollyClient=#amazonPollyClient&operation=startSpeechSynthesisTask")
                        .to("mock:result");

                from("direct:getSpeechSynthesisTask")
                        .to("aws2-polly://test?pollyClient=#amazonPollyClient&operation=getSpeechSynthesisTask")
                        .to("mock:result");

                from("direct:listSpeechSynthesisTasks")
                        .to("aws2-polly://test?pollyClient=#amazonPollyClient&operation=listSpeechSynthesisTasks")
                        .to("mock:result");
            }
        };
    }
}
