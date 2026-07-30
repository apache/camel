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
package org.apache.camel.component.aws2.rekognition;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.amazon.awssdk.services.rekognition.model.*;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Rekognition2ProducerTest extends CamelTestSupport {

    @BindToRegistry("awsRekognitionClient")
    AmazonRekognitionClientMock clientMock = new AmazonRekognitionClientMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void associateFacesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:associateFaces", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.associateFaces);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
                exchange.getIn().setHeader(Rekognition2Constants.USER_ID, "test-user");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        AssociateFacesResponse result = (AssociateFacesResponse) exchange.getIn().getBody();
        assertEquals(1, result.associatedFaces().size());
        assertEquals("test-face-id", result.associatedFaces().iterator().next().faceId());
    }

    @Test
    public void compareFacesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:compareFaces", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.compareFaces);
                exchange.getIn().setHeader(Rekognition2Constants.IMAGE, Image.builder().build());
                exchange.getIn().setHeader(Rekognition2Constants.TARGET_IMAGE, Image.builder().build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        CompareFacesResponse result = (CompareFacesResponse) exchange.getIn().getBody();
        assertEquals(1, result.faceMatches().size());
        assertEquals(98.0f, result.faceMatches().get(0).similarity());
    }

    @Test
    public void createCollectionTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createCollection", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.createCollection);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        CreateCollectionResponse result = (CreateCollectionResponse) exchange.getIn().getBody();
        assertEquals("aws:rekognition-collection::test:arn", result.collectionArn());
        assertEquals(200, result.statusCode());
    }

    @Test
    public void createUserTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createUser", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.createUser);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
                exchange.getIn().setHeader(Rekognition2Constants.USER_ID, "test-user");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        CreateUserResponse result = (CreateUserResponse) exchange.getIn().getBody();
        assertNotNull(result);
    }

    @Test
    public void deleteCollectionTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteCollection", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.deleteCollection);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        DeleteCollectionResponse result = (DeleteCollectionResponse) exchange.getIn().getBody();
        assertEquals(200, result.statusCode());
    }

    @Test
    public void deleteFacesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteFaces", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.deleteFaces);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        DeleteFacesResponse result = (DeleteFacesResponse) exchange.getIn().getBody();
        assertEquals(1, result.deletedFaces().size());
        assertEquals("test-face-id", result.deletedFaces().get(0));
    }

    @Test
    public void deleteUserTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteUser", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.deleteUser);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
                exchange.getIn().setHeader(Rekognition2Constants.USER_ID, "test-user");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        DeleteUserResponse result = (DeleteUserResponse) exchange.getIn().getBody();
        assertNotNull(result);
    }

    @Test
    public void describeCollectionTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeCollection", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.describeCollection);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        DescribeCollectionResponse result = (DescribeCollectionResponse) exchange.getIn().getBody();
        assertEquals("aws:rekognition-collection::test:arn", result.collectionARN());
        assertEquals(10L, result.faceCount());
    }

    @Test
    public void detectFacesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectFaces", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.detectFaces);
                exchange.getIn().setHeader(Rekognition2Constants.IMAGE, Image.builder().build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        DetectFacesResponse result = (DetectFacesResponse) exchange.getIn().getBody();
        assertEquals(1, result.faceDetails().size());
        assertEquals(99.5f, result.faceDetails().get(0).confidence());
    }

    @Test
    public void detectLabelsTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectLabels", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.detectLabels);
                exchange.getIn().setHeader(Rekognition2Constants.IMAGE, Image.builder().build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        DetectLabelsResponse result = (DetectLabelsResponse) exchange.getIn().getBody();
        assertEquals(1, result.labels().size());
        assertEquals("TestLabel", result.labels().get(0).name());
    }

    @Test
    public void detectModerationLabelsTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectModerationLabels", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.detectModerationLabels);
                exchange.getIn().setHeader(Rekognition2Constants.IMAGE, Image.builder().build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        DetectModerationLabelsResponse result = (DetectModerationLabelsResponse) exchange.getIn().getBody();
        assertEquals(1, result.moderationLabels().size());
        assertEquals("TestModerationLabel", result.moderationLabels().get(0).name());
    }

    @Test
    public void detectProtectiveEquipmentTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectProtectiveEquipment", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.detectProtectiveEquipment);
                exchange.getIn().setHeader(Rekognition2Constants.IMAGE, Image.builder().build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        DetectProtectiveEquipmentResponse result = (DetectProtectiveEquipmentResponse) exchange.getIn().getBody();
        assertEquals("1.0", result.protectiveEquipmentModelVersion());
    }

    @Test
    public void detectTextTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectText", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.detectText);
                exchange.getIn().setHeader(Rekognition2Constants.IMAGE, Image.builder().build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        DetectTextResponse result = (DetectTextResponse) exchange.getIn().getBody();
        assertEquals(1, result.textDetections().size());
        assertEquals("TestText", result.textDetections().get(0).detectedText());
    }

    @Test
    public void disassociateFacesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:disassociateFaces", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.disassociateFaces);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
                exchange.getIn().setHeader(Rekognition2Constants.USER_ID, "test-user");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        DisassociateFacesResponse result = (DisassociateFacesResponse) exchange.getIn().getBody();
        assertEquals(1, result.disassociatedFaces().size());
        assertEquals("test-face-id", result.disassociatedFaces().get(0).faceId());
    }

    @Test
    public void getCelebrityInfoTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getCelebrityInfo", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.getCelebrityInfo);
                exchange.getIn().setHeader(Rekognition2Constants.CELEBRITY_ID, "celebrity-123");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        GetCelebrityInfoResponse result = (GetCelebrityInfoResponse) exchange.getIn().getBody();
        assertEquals("TestCelebrity", result.name());
    }

    @Test
    public void getMediaAnalysisJobTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getMediaAnalysisJob", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.getMediaAnalysisJob);
                exchange.getIn().setHeader(Rekognition2Constants.JOB_ID, "test-job-id");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        GetMediaAnalysisJobResponse result = (GetMediaAnalysisJobResponse) exchange.getIn().getBody();
        assertEquals("test-job-id", result.jobId());
        assertEquals(MediaAnalysisJobStatus.SUCCEEDED, result.status());
    }

    @Test
    public void indexFacesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:indexFaces", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.indexFaces);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
                exchange.getIn().setHeader(Rekognition2Constants.IMAGE, Image.builder().build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        IndexFacesResponse result = (IndexFacesResponse) exchange.getIn().getBody();
        assertEquals(1, result.faceRecords().size());
        assertEquals("test-face-id", result.faceRecords().get(0).face().faceId());
    }

    @Test
    public void listCollectionsTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listCollections", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.listCollections);
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        ListCollectionsResponse result = (ListCollectionsResponse) exchange.getIn().getBody();
        assertEquals(1, result.collectionIds().size());
        assertEquals("test-collection", result.collectionIds().get(0));
    }

    @Test
    public void listMediaAnalysisJobTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listMediaAnalysisJobs", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.listMediaAnalysisJobs);
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        ListMediaAnalysisJobsResponse result = (ListMediaAnalysisJobsResponse) exchange.getIn().getBody();
        assertEquals(1, result.mediaAnalysisJobs().size());
        assertEquals("test-job-id", result.mediaAnalysisJobs().get(0).jobId());
    }

    @Test
    public void listFacesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listFaces", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.listFaces);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        ListFacesResponse result = (ListFacesResponse) exchange.getIn().getBody();
        assertEquals(1, result.faces().size());
        assertEquals("test-face-id", result.faces().get(0).faceId());
    }

    @Test
    public void listUsersTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listUsers", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.listUsers);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        ListUsersResponse result = (ListUsersResponse) exchange.getIn().getBody();
        assertEquals(1, result.users().size());
        assertEquals("test-user-id", result.users().get(0).userId());
    }

    @Test
    public void recognizeCelebritiesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:recognizeCelebrities", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.recognizeCelebrities);
                exchange.getIn().setHeader(Rekognition2Constants.IMAGE, Image.builder().build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        RecognizeCelebritiesResponse result = (RecognizeCelebritiesResponse) exchange.getIn().getBody();
        assertEquals(1, result.celebrityFaces().size());
        assertEquals("TestCelebrity", result.celebrityFaces().get(0).name());
    }

    @Test
    public void searchFacesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:searchFaces", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.searchFaces);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
                exchange.getIn().setHeader(Rekognition2Constants.FACE_ID, "test-face-id");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        SearchFacesResponse result = (SearchFacesResponse) exchange.getIn().getBody();
        assertEquals(1, result.faceMatches().size());
        assertEquals(95.0f, result.faceMatches().get(0).similarity());
    }

    @Test
    public void searchFacesByImageTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:searchFacesByImage", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.searchFacesByImage);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
                exchange.getIn().setHeader(Rekognition2Constants.IMAGE, Image.builder().build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        SearchFacesByImageResponse result = (SearchFacesByImageResponse) exchange.getIn().getBody();
        assertEquals(1, result.faceMatches().size());
        assertEquals(93.0f, result.faceMatches().get(0).similarity());
    }

    @Test
    public void searchUsersTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:searchUsers", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.searchUsers);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        SearchUsersResponse result = (SearchUsersResponse) exchange.getIn().getBody();
        assertEquals(1, result.userMatches().size());
        assertEquals(96.0f, result.userMatches().get(0).similarity());
    }

    @Test
    public void searchUsersByImageTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:searchUsersByImage", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.searchUsersByImage);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
                exchange.getIn().setHeader(Rekognition2Constants.IMAGE, Image.builder().build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        SearchUsersByImageResponse result = (SearchUsersByImageResponse) exchange.getIn().getBody();
        assertEquals(1, result.userMatches().size());
        assertEquals(94.0f, result.userMatches().get(0).similarity());
    }

    @Test
    public void startMediaAnalysisJobTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:startMediaAnalysisJob", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.startMediaAnalysisJob);
            }
        });

        MockEndpoint.assertIsSatisfied(context);
        StartMediaAnalysisJobResponse result = (StartMediaAnalysisJobResponse) exchange.getIn().getBody();
        assertEquals("new-job-id", result.jobId());
    }

    @ParameterizedTest
    @CsvSource({
            "direct:associateFacesPojo,associateFaces operation requires AssociateFacesRequest in POJO mode",
            "direct:compareFacesPojo,compareFaces operation requires CompareFacesRequest in POJO mode",
            "direct:createCollectionPojo,createCollection operation requires CreateCollectionRequest in POJO mode",
            "direct:createUserPojo,createUser operation requires CreateUserRequest in POJO mode",
            "direct:deleteCollectionPojo,deleteCollection operation requires DeleteCollectionRequest in POJO mode",
            "direct:deleteFacesPojo,deleteFaces operation requires DeleteFacesRequest in POJO mode",
            "direct:deleteUserPojo,deleteUser operation requires DeleteUserRequest in POJO mode",
            "direct:describeCollectionPojo,describeCollection operation requires DescribeCollectionRequest in POJO mode",
            "direct:detectFacesPojo,detectFaces operation requires DetectFacesRequest in POJO mode",
            "direct:detectLabelsPojo,detectLabels operation requires DetectLabelsRequest in POJO mode",
            "direct:detectModerationLabelsPojo,detectModerationLabels operation requires DetectModerationLabelsRequest in POJO mode",
            "direct:detectProtectiveEquipmentPojo,detectProtectiveEquipment operation requires DetectProtectiveEquipmentRequest in POJO mode",
            "direct:detectTextPojo,detectText operation requires DetectTextRequest in POJO mode",
            "direct:disassociateFacesPojo,disassociateFaces operation requires DisassociateFacesRequest in POJO mode",
            "direct:getCelebrityInfoPojo,getCelebrityInfo operation requires GetCelebrityInfoRequest in POJO mode",
            "direct:getMediaAnalysisJobPojo,getMediaAnalysisJob operation requires GetMediaAnalysisJobRequest in POJO mode",
            "direct:indexFacesPojo,indexFaces operation requires IndexFacesRequest in POJO mode",
            "direct:listCollectionsPojo,listCollections operation requires ListCollectionsRequest in POJO mode",
            "direct:listMediaAnalysisJobsPojo,listMediaAnalysisJobs operation requires ListMediaAnalysisJobsRequest in POJO mode",
            "direct:listFacesPojo,listFaces operation requires ListFacesRequest in POJO mode",
            "direct:listUsersPojo,listUsers operation requires ListUsersRequest in POJO mode",
            "direct:recognizeCelebritiesPojo,recognizeCelebrities operation requires RecognizeCelebritiesRequest in POJO mode",
            "direct:searchFacesPojo,searchFaces operation requires SearchFacesRequest in POJO mode",
            "direct:searchFacesByImagePojo,searchFacesByImage operation requires SearchFacesByImageRequest in POJO mode",
            "direct:searchUsersPojo,searchUsers operation requires SearchUsersRequest in POJO mode",
            "direct:searchUsersByImagePojo,searchUsersByImage operation requires SearchUsersByImageRequest in POJO mode",
            "direct:startMediaAnalysisJobPojo,startMediaAnalysisJob operation requires StartMediaAnalysisJobRequest in POJO mode",
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
                from("direct:associateFaces")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=associateFaces")
                        .to("mock:result");
                from("direct:compareFaces")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=compareFaces")
                        .to("mock:result");
                from("direct:createCollection")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=createCollection")
                        .to("mock:result");
                from("direct:createUser")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=createUser")
                        .to("mock:result");
                from("direct:deleteCollection")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=deleteCollection")
                        .to("mock:result");
                from("direct:deleteFaces")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=deleteFaces")
                        .to("mock:result");
                from("direct:deleteUser")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=deleteUser")
                        .to("mock:result");
                from("direct:describeCollection")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=describeCollection")
                        .to("mock:result");
                from("direct:detectFaces")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=detectFaces")
                        .to("mock:result");
                from("direct:detectLabels")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=detectLabels")
                        .to("mock:result");
                from("direct:detectModerationLabels")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=detectModerationLabels")
                        .to("mock:result");
                from("direct:detectProtectiveEquipment")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=detectProtectiveEquipment")
                        .to("mock:result");
                from("direct:detectText")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=detectText")
                        .to("mock:result");
                from("direct:disassociateFaces")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=disassociateFaces")
                        .to("mock:result");
                from("direct:getCelebrityInfo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=getCelebrityInfo")
                        .to("mock:result");
                from("direct:getMediaAnalysisJob")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=getMediaAnalysisJob")
                        .to("mock:result");
                from("direct:indexFaces")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=indexFaces")
                        .to("mock:result");
                from("direct:listCollections")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=listCollections")
                        .to("mock:result");
                from("direct:listMediaAnalysisJobs")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=listMediaAnalysisJobs")
                        .to("mock:result");
                from("direct:listFaces")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=listFaces")
                        .to("mock:result");
                from("direct:listUsers")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=listUsers")
                        .to("mock:result");
                from("direct:recognizeCelebrities")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=recognizeCelebrities")
                        .to("mock:result");
                from("direct:searchFaces")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=searchFaces")
                        .to("mock:result");
                from("direct:searchFacesByImage")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=searchFacesByImage")
                        .to("mock:result");
                from("direct:searchUsers")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=searchUsers")
                        .to("mock:result");
                from("direct:searchUsersByImage")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=searchUsersByImage")
                        .to("mock:result");
                from("direct:startMediaAnalysisJob")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=startMediaAnalysisJob")
                        .to("mock:result");
                from("direct:associateFacesPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=associateFaces&pojoRequest=true");
                from("direct:compareFacesPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=compareFaces&pojoRequest=true");
                from("direct:createCollectionPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=createCollection&pojoRequest=true");
                from("direct:createUserPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=createUser&pojoRequest=true");
                from("direct:deleteCollectionPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=deleteCollection&pojoRequest=true");
                from("direct:deleteFacesPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=deleteFaces&pojoRequest=true");
                from("direct:deleteUserPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=deleteUser&pojoRequest=true");
                from("direct:describeCollectionPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=describeCollection&pojoRequest=true");
                from("direct:detectFacesPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=detectFaces&pojoRequest=true");
                from("direct:detectLabelsPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=detectLabels&pojoRequest=true");
                from("direct:detectModerationLabelsPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=detectModerationLabels&pojoRequest=true");
                from("direct:detectProtectiveEquipmentPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=detectProtectiveEquipment&pojoRequest=true");
                from("direct:detectTextPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=detectText&pojoRequest=true");
                from("direct:disassociateFacesPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=disassociateFaces&pojoRequest=true");
                from("direct:getCelebrityInfoPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=getCelebrityInfo&pojoRequest=true");
                from("direct:getMediaAnalysisJobPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=getMediaAnalysisJob&pojoRequest=true");
                from("direct:indexFacesPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=indexFaces&pojoRequest=true");
                from("direct:listCollectionsPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=listCollections&pojoRequest=true");
                from("direct:listMediaAnalysisJobsPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=listMediaAnalysisJobs&pojoRequest=true");
                from("direct:listFacesPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=listFaces&pojoRequest=true");
                from("direct:listUsersPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=listUsers&pojoRequest=true");
                from("direct:recognizeCelebritiesPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=recognizeCelebrities&pojoRequest=true");
                from("direct:searchFacesPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=searchFaces&pojoRequest=true");
                from("direct:searchFacesByImagePojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=searchFacesByImage&pojoRequest=true");
                from("direct:searchUsersPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=searchUsers&pojoRequest=true");
                from("direct:searchUsersByImagePojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=searchUsersByImage&pojoRequest=true");
                from("direct:startMediaAnalysisJobPojo")
                        .to("aws2-rekognition://test?awsRekognitionClient=#awsRekognitionClient&operation=startMediaAnalysisJob&pojoRequest=true");
            }
        };
    }
}
