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
package org.apache.camel.component.aws2.transcribe;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.transcribe.model.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Transcribe2ProducerTest extends CamelTestSupport {

    @BindToRegistry("amazonTranscribeClient")
    AmazonAWSTranscribeMock clientMock = new AmazonAWSTranscribeMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void transcribeStartTranscriptionJobTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:startTranscriptionJob", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Transcribe2Constants.TRANSCRIPTION_JOB_NAME, "test-job");
                exchange.getIn().setHeader(Transcribe2Constants.LANGUAGE_CODE, "en-US");
                exchange.getIn().setHeader(Transcribe2Constants.MEDIA_FORMAT, "mp3");
                exchange.getIn().setHeader(Transcribe2Constants.MEDIA_URI, "s3://mybucket/myfile.mp3");
            }
        });

        StartTranscriptionJobResponse resultGet = (StartTranscriptionJobResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals("test-job", resultGet.transcriptionJob().transcriptionJobName());
        assertEquals(TranscriptionJobStatus.IN_PROGRESS, resultGet.transcriptionJob().transcriptionJobStatus());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void transcribeGetTranscriptionJobTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getTranscriptionJob", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Transcribe2Constants.TRANSCRIPTION_JOB_NAME, "test-job");
            }
        });

        GetTranscriptionJobResponse resultGet = (GetTranscriptionJobResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals("test-job", resultGet.transcriptionJob().transcriptionJobName());
        assertEquals(TranscriptionJobStatus.COMPLETED, resultGet.transcriptionJob().transcriptionJobStatus());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void transcribeListTranscriptionJobsTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listTranscriptionJobs", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Transcribe2Constants.STATUS, "COMPLETED");
            }
        });

        ListTranscriptionJobsResponse resultGet = (ListTranscriptionJobsResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals(1, resultGet.transcriptionJobSummaries().size());
        assertEquals("test-job", resultGet.transcriptionJobSummaries().get(0).transcriptionJobName());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void transcribeDeleteTranscriptionJobTest() throws Exception {

        mock.expectedMessageCount(1);
        template.request("direct:deleteTranscriptionJob", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Transcribe2Constants.TRANSCRIPTION_JOB_NAME, "test-job");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void transcribeCreateVocabularyTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createVocabulary", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Transcribe2Constants.VOCABULARY_NAME, "test-vocabulary");
                exchange.getIn().setHeader(Transcribe2Constants.LANGUAGE_CODE, "en-US");
            }
        });

        CreateVocabularyResponse resultGet = (CreateVocabularyResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals("test-vocabulary", resultGet.vocabularyName());
        assertEquals(VocabularyState.PENDING, resultGet.vocabularyState());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void transcribeGetVocabularyTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getVocabulary", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Transcribe2Constants.VOCABULARY_NAME, "test-vocabulary");
            }
        });

        GetVocabularyResponse resultGet = (GetVocabularyResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals("test-vocabulary", resultGet.vocabularyName());
        assertEquals(VocabularyState.READY, resultGet.vocabularyState());
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:startTranscriptionJob")
                        .to("aws2-transcribe://transcribe?operation=startTranscriptionJob&transcribeClient=#amazonTranscribeClient")
                        .to("mock:result");

                from("direct:getTranscriptionJob")
                        .to("aws2-transcribe://transcribe?operation=getTranscriptionJob&transcribeClient=#amazonTranscribeClient")
                        .to("mock:result");

                from("direct:listTranscriptionJobs")
                        .to("aws2-transcribe://transcribe?operation=listTranscriptionJobs&transcribeClient=#amazonTranscribeClient")
                        .to("mock:result");

                from("direct:deleteTranscriptionJob")
                        .to("aws2-transcribe://transcribe?operation=deleteTranscriptionJob&transcribeClient=#amazonTranscribeClient")
                        .to("mock:result");

                from("direct:createVocabulary")
                        .to("aws2-transcribe://transcribe?operation=createVocabulary&transcribeClient=#amazonTranscribeClient")
                        .to("mock:result");

                from("direct:getVocabulary")
                        .to("aws2-transcribe://transcribe?operation=getVocabulary&transcribeClient=#amazonTranscribeClient")
                        .to("mock:result");
            }
        };
    }
}
