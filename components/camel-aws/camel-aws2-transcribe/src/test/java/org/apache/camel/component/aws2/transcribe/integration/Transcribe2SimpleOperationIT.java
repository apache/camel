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

package org.apache.camel.component.aws2.transcribe.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.transcribe.Transcribe2Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import software.amazon.awssdk.services.transcribe.model.*;

@DisabledOnOs(architectures = {"s390x", "ppc64le"})
public class Transcribe2SimpleOperationIT extends Aws2TranscribeBase {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @BeforeEach
    public void resetMockEndpoint() {
        result.reset();
    }

    @Test
    public void sendStartTranscriptionJob() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:startTranscriptionJob", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Transcribe2Constants.TRANSCRIPTION_JOB_NAME, "test-job-" + name.get());
                exchange.getIn().setHeader(Transcribe2Constants.LANGUAGE_CODE, "en-US");
                exchange.getIn().setHeader(Transcribe2Constants.MEDIA_FORMAT, "mp3");
                exchange.getIn().setHeader(Transcribe2Constants.MEDIA_URI, "s3://test-bucket/test-file.mp3");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(1, result.getExchanges().size());
        Exchange exchange = result.getExchanges().get(0);
        StartTranscriptionJobResponse response = exchange.getIn().getBody(StartTranscriptionJobResponse.class);
        assertNotNull(response);
        assertNotNull(response.transcriptionJob());
        assertEquals("test-job-" + name.get(), response.transcriptionJob().transcriptionJobName());
    }

    @Test
    public void sendListTranscriptionJobs() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:listTranscriptionJobs", new Processor() {
            @Override
            public void process(Exchange exchange) {
                // No headers needed for list operation
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(1, result.getExchanges().size());
        Exchange exchange = result.getExchanges().get(0);
        ListTranscriptionJobsResponse response = exchange.getIn().getBody(ListTranscriptionJobsResponse.class);
        assertNotNull(response);
        assertNotNull(response.transcriptionJobSummaries());
    }

    @Test
    public void sendCreateVocabulary() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:createVocabulary", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Transcribe2Constants.VOCABULARY_NAME, "test-vocabulary-" + name.get());
                exchange.getIn().setHeader(Transcribe2Constants.LANGUAGE_CODE, "en-US");
                exchange.getIn().setHeader(Transcribe2Constants.VOCABULARY_PHRASES, "test,test-1,test-2");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(1, result.getExchanges().size());
        Exchange exchange = result.getExchanges().get(0);
        CreateVocabularyResponse response = exchange.getIn().getBody(CreateVocabularyResponse.class);
        assertNotNull(response);
        assertEquals("test-vocabulary-" + name.get(), response.vocabularyName());
        assertEquals(LanguageCode.EN_US, response.languageCode());
    }

    @Test
    public void sendListVocabularies() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:listVocabularies", new Processor() {
            @Override
            public void process(Exchange exchange) {
                // No headers needed for list operation
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        assertEquals(1, result.getExchanges().size());
        Exchange exchange = result.getExchanges().get(0);
        ListVocabulariesResponse response = exchange.getIn().getBody(ListVocabulariesResponse.class);
        assertNotNull(response);
        assertNotNull(response.vocabularies());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:startTranscriptionJob")
                        .to("aws2-transcribe://transcribe?operation=startTranscriptionJob")
                        .to("mock:result");

                from("direct:getTranscriptionJob")
                        .to("aws2-transcribe://transcribe?operation=getTranscriptionJob")
                        .to("mock:result");

                from("direct:listTranscriptionJobs")
                        .to("aws2-transcribe://transcribe?operation=listTranscriptionJobs")
                        .to("mock:result");

                from("direct:deleteTranscriptionJob")
                        .to("aws2-transcribe://transcribe?operation=deleteTranscriptionJob")
                        .to("mock:result");

                from("direct:createVocabulary")
                        .to("aws2-transcribe://transcribe?operation=createVocabulary")
                        .to("mock:result");

                from("direct:getVocabulary")
                        .to("aws2-transcribe://transcribe?operation=getVocabulary")
                        .to("mock:result");

                from("direct:listVocabularies")
                        .to("aws2-transcribe://transcribe?operation=listVocabularies")
                        .to("mock:result");

                from("direct:updateVocabulary")
                        .to("aws2-transcribe://transcribe?operation=updateVocabulary")
                        .to("mock:result");

                from("direct:deleteVocabulary")
                        .to("aws2-transcribe://transcribe?operation=deleteVocabulary")
                        .to("mock:result");
            }
        };
    }
}
