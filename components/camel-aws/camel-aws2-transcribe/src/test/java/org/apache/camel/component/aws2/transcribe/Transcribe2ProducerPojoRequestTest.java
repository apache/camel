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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Transcribe2ProducerPojoRequestTest extends CamelTestSupport {

    @BindToRegistry("amazonTranscribeClient")
    AmazonAWSTranscribeMock clientMock = new AmazonAWSTranscribeMock();

    @ParameterizedTest
    @CsvSource({
            "direct:startTranscriptionJob,startTranscriptionJob operation requires StartTranscriptionJobRequest in POJO mode",
            "direct:getTranscriptionJob,getTranscriptionJob operation requires GetTranscriptionJobRequest in POJO mode",
            "direct:listTranscriptionJobs,listTranscriptionJobs operation requires ListTranscriptionJobsRequest in POJO mode",
            "direct:deleteTranscriptionJob,deleteTranscriptionJob operation requires DeleteTranscriptionJobRequest in POJO mode",
            "direct:createVocabulary,createVocabulary operation requires CreateVocabularyRequest in POJO mode",
            "direct:getVocabulary,getVocabulary operation requires GetVocabularyRequest in POJO mode",
            "direct:listVocabularies,listVocabularies operation requires ListVocabulariesRequest in POJO mode",
            "direct:updateVocabulary,updateVocabulary operation requires UpdateVocabularyRequest in POJO mode",
            "direct:deleteVocabulary,deleteVocabulary operation requires DeleteVocabularyRequest in POJO mode",
            "direct:createVocabularyFilter,createVocabularyFilter operation requires CreateVocabularyFilterRequest in POJO mode",
            "direct:getVocabularyFilter,getVocabularyFilter operation requires GetVocabularyFilterRequest in POJO mode",
            "direct:listVocabularyFilters,listVocabularyFilters operation requires ListVocabularyFiltersRequest in POJO mode",
            "direct:updateVocabularyFilter,updateVocabularyFilter operation requires UpdateVocabularyFilterRequest in POJO mode",
            "direct:deleteVocabularyFilter,deleteVocabularyFilter operation requires DeleteVocabularyFilterRequest in POJO mode",
            "direct:createLanguageModel,createLanguageModel operation requires CreateLanguageModelRequest in POJO mode",
            "direct:describeLanguageModel,describeLanguageModel operation requires DescribeLanguageModelRequest in POJO mode",
            "direct:listLanguageModels,listLanguageModels operation requires ListLanguageModelsRequest in POJO mode",
            "direct:deleteLanguageModel,deleteLanguageModel operation requires DeleteLanguageModelRequest in POJO mode",
            "direct:createMedicalVocabulary,createMedicalVocabulary operation requires CreateMedicalVocabularyRequest in POJO mode",
            "direct:getMedicalVocabulary,getMedicalVocabulary operation requires GetMedicalVocabularyRequest in POJO mode",
            "direct:listMedicalVocabularies,listMedicalVocabularies operation requires ListMedicalVocabulariesRequest in POJO mode",
            "direct:updateMedicalVocabulary,updateMedicalVocabulary operation requires UpdateMedicalVocabularyRequest in POJO mode",
            "direct:deleteMedicalVocabulary,deleteMedicalVocabulary operation requires DeleteMedicalVocabularyRequest in POJO mode",
            "direct:startMedicalTranscriptionJob,startMedicalTranscriptionJob operation requires StartMedicalTranscriptionJobRequest in POJO mode",
            "direct:getMedicalTranscriptionJob,getMedicalTranscriptionJob operation requires GetMedicalTranscriptionJobRequest in POJO mode",
            "direct:listMedicalTranscriptionJobs,listMedicalTranscriptionJobs operation requires ListMedicalTranscriptionJobsRequest in POJO mode",
            "direct:deleteMedicalTranscriptionJob,deleteMedicalTranscriptionJob operation requires DeleteMedicalTranscriptionJobRequest in POJO mode",
            "direct:tagResource,tagResource operation requires TagResourceRequest in POJO mode",
            "direct:untagResource,untagResource operation requires UntagResourceRequest in POJO mode",
            "direct:listTagsForResource,listTagsForResource operation requires ListTagsForResourceRequest in POJO mode",
    })
    void pojoRequestWithWrongBodyTypeThrows(String route, String expectedMessage) {
        assertThatThrownBy(() -> template.requestBody(route, "not the expected request type"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage(expectedMessage);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:startTranscriptionJob")
                        .to("aws2-transcribe://transcribe?operation=startTranscriptionJob&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:getTranscriptionJob")
                        .to("aws2-transcribe://transcribe?operation=getTranscriptionJob&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:listTranscriptionJobs")
                        .to("aws2-transcribe://transcribe?operation=listTranscriptionJobs&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:deleteTranscriptionJob")
                        .to("aws2-transcribe://transcribe?operation=deleteTranscriptionJob&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:createVocabulary")
                        .to("aws2-transcribe://transcribe?operation=createVocabulary&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:getVocabulary")
                        .to("aws2-transcribe://transcribe?operation=getVocabulary&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:listVocabularies")
                        .to("aws2-transcribe://transcribe?operation=listVocabularies&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:updateVocabulary")
                        .to("aws2-transcribe://transcribe?operation=updateVocabulary&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:deleteVocabulary")
                        .to("aws2-transcribe://transcribe?operation=deleteVocabulary&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:createVocabularyFilter")
                        .to("aws2-transcribe://transcribe?operation=createVocabularyFilter&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:getVocabularyFilter")
                        .to("aws2-transcribe://transcribe?operation=getVocabularyFilter&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:listVocabularyFilters")
                        .to("aws2-transcribe://transcribe?operation=listVocabularyFilters&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:updateVocabularyFilter")
                        .to("aws2-transcribe://transcribe?operation=updateVocabularyFilter&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:deleteVocabularyFilter")
                        .to("aws2-transcribe://transcribe?operation=deleteVocabularyFilter&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:createLanguageModel")
                        .to("aws2-transcribe://transcribe?operation=createLanguageModel&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:describeLanguageModel")
                        .to("aws2-transcribe://transcribe?operation=describeLanguageModel&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:listLanguageModels")
                        .to("aws2-transcribe://transcribe?operation=listLanguageModels&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:deleteLanguageModel")
                        .to("aws2-transcribe://transcribe?operation=deleteLanguageModel&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:createMedicalVocabulary")
                        .to("aws2-transcribe://transcribe?operation=createMedicalVocabulary&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:getMedicalVocabulary")
                        .to("aws2-transcribe://transcribe?operation=getMedicalVocabulary&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:listMedicalVocabularies")
                        .to("aws2-transcribe://transcribe?operation=listMedicalVocabularies&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:updateMedicalVocabulary")
                        .to("aws2-transcribe://transcribe?operation=updateMedicalVocabulary&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:deleteMedicalVocabulary")
                        .to("aws2-transcribe://transcribe?operation=deleteMedicalVocabulary&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:startMedicalTranscriptionJob")
                        .to("aws2-transcribe://transcribe?operation=startMedicalTranscriptionJob&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:getMedicalTranscriptionJob")
                        .to("aws2-transcribe://transcribe?operation=getMedicalTranscriptionJob&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:listMedicalTranscriptionJobs")
                        .to("aws2-transcribe://transcribe?operation=listMedicalTranscriptionJobs&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:deleteMedicalTranscriptionJob")
                        .to("aws2-transcribe://transcribe?operation=deleteMedicalTranscriptionJob&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:tagResource")
                        .to("aws2-transcribe://transcribe?operation=tagResource&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:untagResource")
                        .to("aws2-transcribe://transcribe?operation=untagResource&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
                from("direct:listTagsForResource")
                        .to("aws2-transcribe://transcribe?operation=listTagsForResource&transcribeClient=#amazonTranscribeClient&pojoRequest=true");
            }
        };
    }
}
