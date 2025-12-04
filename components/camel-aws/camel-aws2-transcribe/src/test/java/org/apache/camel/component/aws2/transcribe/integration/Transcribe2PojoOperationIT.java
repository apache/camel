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
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import software.amazon.awssdk.services.transcribe.model.*;

@DisabledOnOs(architectures = {"s390x", "ppc64le"})
public class Transcribe2PojoOperationIT extends Aws2TranscribeBase {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendStartTranscriptionJobPojo() throws Exception {
        result.reset();
        result.expectedMessageCount(1);

        template.send("direct:startTranscriptionJobPojo", new Processor() {
            @Override
            public void process(Exchange exchange) {
                StartTranscriptionJobRequest request = StartTranscriptionJobRequest.builder()
                        .transcriptionJobName("test-pojo-job-test")
                        .languageCode(LanguageCode.EN_US)
                        .mediaFormat(MediaFormat.MP3)
                        .media(Media.builder()
                                .mediaFileUri("s3://test-bucket/test-file.mp3")
                                .build())
                        .build();
                exchange.getIn().setBody(request);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = result.getExchanges().get(0);
        StartTranscriptionJobResponse response = exchange.getIn().getBody(StartTranscriptionJobResponse.class);
        assertNotNull(response);
        assertNotNull(response.transcriptionJob());
        assertEquals("test-pojo-job-test", response.transcriptionJob().transcriptionJobName());
    }

    @Test
    public void sendGetTranscriptionJobPojo() throws Exception {
        result.reset();
        result.expectedMessageCount(1);

        template.send("direct:getTranscriptionJobPojo", new Processor() {
            @Override
            public void process(Exchange exchange) {
                GetTranscriptionJobRequest request = GetTranscriptionJobRequest.builder()
                        .transcriptionJobName("test-pojo-job-test")
                        .build();
                exchange.getIn().setBody(request);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = result.getExchanges().get(0);
        GetTranscriptionJobResponse response = exchange.getIn().getBody(GetTranscriptionJobResponse.class);
        assertNotNull(response);
        assertNotNull(response.transcriptionJob());
    }

    @Test
    public void sendCreateVocabularyPojo() throws Exception {
        result.reset();
        result.expectedMessageCount(1);

        template.send("direct:createVocabularyPojo", new Processor() {
            @Override
            public void process(Exchange exchange) {
                CreateVocabularyRequest request = CreateVocabularyRequest.builder()
                        .vocabularyName("test-pojo-vocabulary-" + name.get())
                        .languageCode(LanguageCode.EN_US)
                        .phrases("test-1,test-2,test-3")
                        .build();
                exchange.getIn().setBody(request);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = result.getExchanges().get(0);
        CreateVocabularyResponse response = exchange.getIn().getBody(CreateVocabularyResponse.class);
        assertNotNull(response);
        assertEquals("test-pojo-vocabulary-" + name.get(), response.vocabularyName());
        assertEquals(LanguageCode.EN_US, response.languageCode());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:startTranscriptionJobPojo")
                        .to("aws2-transcribe://transcribe?operation=startTranscriptionJob&pojoRequest=true")
                        .to("mock:result");

                from("direct:getTranscriptionJobPojo")
                        .to("aws2-transcribe://transcribe?operation=getTranscriptionJob&pojoRequest=true")
                        .to("mock:result");

                from("direct:listTranscriptionJobsPojo")
                        .to("aws2-transcribe://transcribe?operation=listTranscriptionJobs&pojoRequest=true")
                        .to("mock:result");

                from("direct:deleteTranscriptionJobPojo")
                        .to("aws2-transcribe://transcribe?operation=deleteTranscriptionJob&pojoRequest=true")
                        .to("mock:result");

                from("direct:createVocabularyPojo")
                        .to("aws2-transcribe://transcribe?operation=createVocabulary&pojoRequest=true")
                        .to("mock:result");

                from("direct:getVocabularyPojo")
                        .to("aws2-transcribe://transcribe?operation=getVocabulary&pojoRequest=true")
                        .to("mock:result");

                from("direct:listVocabulariesPojo")
                        .to("aws2-transcribe://transcribe?operation=listVocabularies&pojoRequest=true")
                        .to("mock:result");

                from("direct:updateVocabularyPojo")
                        .to("aws2-transcribe://transcribe?operation=updateVocabulary&pojoRequest=true")
                        .to("mock:result");

                from("direct:deleteVocabularyPojo")
                        .to("aws2-transcribe://transcribe?operation=deleteVocabulary&pojoRequest=true")
                        .to("mock:result");
            }
        };
    }
}
