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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit6.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import software.amazon.awssdk.services.rekognition.model.AssociateFacesResponse;
import software.amazon.awssdk.services.rekognition.model.CompareFacesResponse;
import software.amazon.awssdk.services.rekognition.model.CreateCollectionResponse;
import software.amazon.awssdk.services.rekognition.model.DeleteCollectionResponse;
import software.amazon.awssdk.services.rekognition.model.DetectFacesResponse;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.DetectModerationLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.DetectProtectiveEquipmentResponse;
import software.amazon.awssdk.services.rekognition.model.DisassociateFacesResponse;
import software.amazon.awssdk.services.rekognition.model.GetMediaAnalysisJobResponse;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.IndexFacesResponse;
import software.amazon.awssdk.services.rekognition.model.ListCollectionsResponse;
import software.amazon.awssdk.services.rekognition.model.ListMediaAnalysisJobsResponse;
import software.amazon.awssdk.services.rekognition.model.SearchFacesResponse;
import software.amazon.awssdk.services.rekognition.model.SearchUsersByImageResponse;
import software.amazon.awssdk.services.rekognition.model.SearchUsersResponse;
import software.amazon.awssdk.services.rekognition.model.StartMediaAnalysisJobResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Rekognition2ProducerSpringTest extends CamelSpringTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void rekognitionCompareFacesTest() throws Exception {
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

        CompareFacesResponse resultGet = (CompareFacesResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals(1, resultGet.faceMatches().size());
        assertEquals(98.0f, resultGet.faceMatches().get(0).similarity());
    }

    @Test
    public void rekognitionCreateCollectionTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createCollection", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.createCollection);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        CreateCollectionResponse resultGet = (CreateCollectionResponse) exchange.getIn().getBody();
        assertEquals("aws:rekognition-collection::test:arn", resultGet.collectionArn());
        assertEquals(200, resultGet.statusCode());
    }

    @Test
    public void rekognitionDeleteCollectionTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteCollection", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.deleteCollection);
                exchange.getIn().setHeader(Rekognition2Constants.COLLECTION_ID, "test-collection");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DeleteCollectionResponse resultGet = (DeleteCollectionResponse) exchange.getIn().getBody();
        assertEquals(200, resultGet.statusCode());
    }

    @Test
    public void rekognitionDetectFacesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectFaces", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.detectFaces);
                exchange.getIn().setHeader(Rekognition2Constants.IMAGE, Image.builder().build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DetectFacesResponse resultGet = (DetectFacesResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.faceDetails().size());
        assertEquals(99.5f, resultGet.faceDetails().get(0).confidence());
    }

    @Test
    public void rekognitionDetectLabelsTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectLabels", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.detectLabels);
                exchange.getIn().setHeader(Rekognition2Constants.IMAGE, Image.builder().build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DetectLabelsResponse resultGet = (DetectLabelsResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.labels().size());
        assertEquals("TestLabel", resultGet.labels().get(0).name());
    }

    @Test
    public void rekognitionIndexFacesTest() throws Exception {
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

        IndexFacesResponse resultGet = (IndexFacesResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.faceRecords().size());
        assertEquals("test-face-id", resultGet.faceRecords().get(0).face().faceId());
    }

    @Test
    public void rekognitionListCollectionsTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listCollections", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.listCollections);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListCollectionsResponse resultGet = (ListCollectionsResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.collectionIds().size());
        assertEquals("test-collection", resultGet.collectionIds().get(0));
    }

    @Test
    public void rekognitionSearchFacesTest() throws Exception {
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

        SearchFacesResponse resultGet = (SearchFacesResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.faceMatches().size());
        assertEquals(95.0f, resultGet.faceMatches().get(0).similarity());
    }

    @Test
    public void rekognitionAssociateFacesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:associateFaces", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.associateFaces);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        AssociateFacesResponse result = (AssociateFacesResponse) exchange.getIn().getBody();
        assertEquals(1, result.associatedFaces().size());
    }

    @Test
    public void rekognitionDetectModerationLabelsTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectModerationLabels", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.detectModerationLabels);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DetectModerationLabelsResponse result = (DetectModerationLabelsResponse) exchange.getIn().getBody();
        assertEquals(1, result.moderationLabels().size());
    }

    @Test
    public void rekognitionDetectProtectiveEquipmentTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detectProtectiveEquipment", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.detectProtectiveEquipment);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DetectProtectiveEquipmentResponse result = (DetectProtectiveEquipmentResponse) exchange.getIn().getBody();
        assertNotNull(result.protectiveEquipmentModelVersion());
    }

    @Test
    public void rekognitionDisassociateFacesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:disassociateFaces", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.disassociateFaces);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DisassociateFacesResponse result = (DisassociateFacesResponse) exchange.getIn().getBody();
        assertEquals(1, result.disassociatedFaces().size());
    }

    @Test
    public void rekognitionGetMediaAnalysisJobTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getMediaAnalysisJob", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.getMediaAnalysisJob);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        GetMediaAnalysisJobResponse result = (GetMediaAnalysisJobResponse) exchange.getIn().getBody();
        assertEquals("test-job-id", result.jobId());
    }

    @Test
    public void rekognitionListMediaAnalysisJobTest() throws Exception {
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
    }

    @Test
    public void rekognitionSearchUsersTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:searchUsers", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.searchUsers);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        SearchUsersResponse result = (SearchUsersResponse) exchange.getIn().getBody();
        assertEquals(1, result.userMatches().size());
    }

    @Test
    public void rekognitionSearchUsersByImageTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:searchUsersByImage", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Rekognition2Constants.OPERATION, Rekognition2Operations.searchUsersByImage);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        SearchUsersByImageResponse result = (SearchUsersByImageResponse) exchange.getIn().getBody();
        assertEquals(1, result.userMatches().size());
    }

    @Test
    public void rekognitionStartMediaAnalysisJobTest() throws Exception {
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

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/component/aws2/rekognition/RekognitionComponentSpringTest-context.xml");
    }
}
