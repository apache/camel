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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.AssociateFacesRequest;
import software.amazon.awssdk.services.rekognition.model.AssociateFacesResponse;
import software.amazon.awssdk.services.rekognition.model.AssociatedFace;
import software.amazon.awssdk.services.rekognition.model.Celebrity;
import software.amazon.awssdk.services.rekognition.model.CompareFacesMatch;
import software.amazon.awssdk.services.rekognition.model.CompareFacesRequest;
import software.amazon.awssdk.services.rekognition.model.CompareFacesResponse;
import software.amazon.awssdk.services.rekognition.model.ComparedFace;
import software.amazon.awssdk.services.rekognition.model.CreateCollectionRequest;
import software.amazon.awssdk.services.rekognition.model.CreateCollectionResponse;
import software.amazon.awssdk.services.rekognition.model.CreateUserRequest;
import software.amazon.awssdk.services.rekognition.model.CreateUserResponse;
import software.amazon.awssdk.services.rekognition.model.DeleteCollectionRequest;
import software.amazon.awssdk.services.rekognition.model.DeleteCollectionResponse;
import software.amazon.awssdk.services.rekognition.model.DeleteFacesRequest;
import software.amazon.awssdk.services.rekognition.model.DeleteFacesResponse;
import software.amazon.awssdk.services.rekognition.model.DeleteUserRequest;
import software.amazon.awssdk.services.rekognition.model.DeleteUserResponse;
import software.amazon.awssdk.services.rekognition.model.DescribeCollectionRequest;
import software.amazon.awssdk.services.rekognition.model.DescribeCollectionResponse;
import software.amazon.awssdk.services.rekognition.model.DetectFacesRequest;
import software.amazon.awssdk.services.rekognition.model.DetectFacesResponse;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.DetectLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.DetectModerationLabelsRequest;
import software.amazon.awssdk.services.rekognition.model.DetectModerationLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.DetectProtectiveEquipmentRequest;
import software.amazon.awssdk.services.rekognition.model.DetectProtectiveEquipmentResponse;
import software.amazon.awssdk.services.rekognition.model.DetectTextRequest;
import software.amazon.awssdk.services.rekognition.model.DetectTextResponse;
import software.amazon.awssdk.services.rekognition.model.DisassociateFacesRequest;
import software.amazon.awssdk.services.rekognition.model.DisassociateFacesResponse;
import software.amazon.awssdk.services.rekognition.model.DisassociatedFace;
import software.amazon.awssdk.services.rekognition.model.Face;
import software.amazon.awssdk.services.rekognition.model.FaceDetail;
import software.amazon.awssdk.services.rekognition.model.FaceMatch;
import software.amazon.awssdk.services.rekognition.model.FaceRecord;
import software.amazon.awssdk.services.rekognition.model.GetCelebrityInfoRequest;
import software.amazon.awssdk.services.rekognition.model.GetCelebrityInfoResponse;
import software.amazon.awssdk.services.rekognition.model.GetMediaAnalysisJobRequest;
import software.amazon.awssdk.services.rekognition.model.GetMediaAnalysisJobResponse;
import software.amazon.awssdk.services.rekognition.model.IndexFacesRequest;
import software.amazon.awssdk.services.rekognition.model.IndexFacesResponse;
import software.amazon.awssdk.services.rekognition.model.Label;
import software.amazon.awssdk.services.rekognition.model.ListCollectionsRequest;
import software.amazon.awssdk.services.rekognition.model.ListCollectionsResponse;
import software.amazon.awssdk.services.rekognition.model.ListFacesRequest;
import software.amazon.awssdk.services.rekognition.model.ListFacesResponse;
import software.amazon.awssdk.services.rekognition.model.ListMediaAnalysisJobsRequest;
import software.amazon.awssdk.services.rekognition.model.ListMediaAnalysisJobsResponse;
import software.amazon.awssdk.services.rekognition.model.ListUsersRequest;
import software.amazon.awssdk.services.rekognition.model.ListUsersResponse;
import software.amazon.awssdk.services.rekognition.model.MatchedUser;
import software.amazon.awssdk.services.rekognition.model.MediaAnalysisJobDescription;
import software.amazon.awssdk.services.rekognition.model.MediaAnalysisJobStatus;
import software.amazon.awssdk.services.rekognition.model.ModerationLabel;
import software.amazon.awssdk.services.rekognition.model.RecognizeCelebritiesRequest;
import software.amazon.awssdk.services.rekognition.model.RecognizeCelebritiesResponse;
import software.amazon.awssdk.services.rekognition.model.SearchFacesByImageRequest;
import software.amazon.awssdk.services.rekognition.model.SearchFacesByImageResponse;
import software.amazon.awssdk.services.rekognition.model.SearchFacesRequest;
import software.amazon.awssdk.services.rekognition.model.SearchFacesResponse;
import software.amazon.awssdk.services.rekognition.model.SearchUsersByImageRequest;
import software.amazon.awssdk.services.rekognition.model.SearchUsersByImageResponse;
import software.amazon.awssdk.services.rekognition.model.SearchUsersRequest;
import software.amazon.awssdk.services.rekognition.model.SearchUsersResponse;
import software.amazon.awssdk.services.rekognition.model.StartMediaAnalysisJobRequest;
import software.amazon.awssdk.services.rekognition.model.StartMediaAnalysisJobResponse;
import software.amazon.awssdk.services.rekognition.model.TextDetection;
import software.amazon.awssdk.services.rekognition.model.User;
import software.amazon.awssdk.services.rekognition.model.UserMatch;

public class AmazonRekognitionClientMock implements RekognitionClient {

    public AmazonRekognitionClientMock() {
    }

    @Override
    public AssociateFacesResponse associateFaces(AssociateFacesRequest associateFacesRequest) {
        AssociateFacesResponse.Builder result = AssociateFacesResponse.builder();
        AssociatedFace associatedFace = AssociatedFace.builder()
                .faceId("test-face-id")
                .build();
        result.associatedFaces(Collections.singleton(associatedFace));
        return result.build();
    }

    @Override
    public CompareFacesResponse compareFaces(CompareFacesRequest compareFacesRequest) {
        CompareFacesResponse.Builder result = CompareFacesResponse.builder();
        ComparedFace face = ComparedFace.builder()
                .confidence(99.5f)
                .build();
        result.faceMatches(Collections.singleton(CompareFacesMatch.builder().face(face).similarity(98.0f).build()));
        return result.build();
    }

    @Override
    public CreateCollectionResponse createCollection(CreateCollectionRequest createCollectionRequest) {
        CreateCollectionResponse.Builder result = CreateCollectionResponse.builder();
        result.collectionArn("aws:rekognition-collection::test:arn");
        result.statusCode(200);
        return result.build();
    }

    @Override
    public CreateUserResponse createUser(CreateUserRequest createUserRequest) {
        CreateUserResponse.Builder result = CreateUserResponse.builder();
        return result.build();
    }

    @Override
    public DeleteCollectionResponse deleteCollection(DeleteCollectionRequest deleteCollectionRequest) {
        DeleteCollectionResponse.Builder result = DeleteCollectionResponse.builder();
        result.statusCode(200);
        return result.build();
    }

    @Override
    public DeleteFacesResponse deleteFaces(DeleteFacesRequest deleteFacesRequest) {
        DeleteFacesResponse.Builder result = DeleteFacesResponse.builder();
        result.deletedFaces(Collections.singletonList("test-face-id"));
        return result.build();
    }

    @Override
    public DeleteUserResponse deleteUser(DeleteUserRequest deleteUserRequest) {
        DeleteUserResponse.Builder result = DeleteUserResponse.builder();
        return result.build();
    }

    @Override
    public DescribeCollectionResponse describeCollection(DescribeCollectionRequest describeCollectionRequest) {
        DescribeCollectionResponse.Builder result = DescribeCollectionResponse.builder();
        result.collectionARN("aws:rekognition-collection::test:arn");
        result.faceCount(10L);
        return result.build();
    }

    @Override
    public DetectFacesResponse detectFaces(DetectFacesRequest detectFacesRequest) {
        DetectFacesResponse.Builder result = DetectFacesResponse.builder();
        FaceDetail face = FaceDetail.builder()
                .confidence(99.5f)
                .build();
        result.faceDetails(Collections.singletonList(face));
        return result.build();
    }

    @Override
    public DetectLabelsResponse detectLabels(DetectLabelsRequest detectLabelsRequest) {
        DetectLabelsResponse.Builder result = DetectLabelsResponse.builder();
        Label label = Label.builder()
                .name("TestLabel")
                .confidence(95.0f)
                .build();
        result.labels(Collections.singletonList(label));
        return result.build();
    }

    @Override
    public DetectModerationLabelsResponse detectModerationLabels(DetectModerationLabelsRequest detectModerationLabelsRequest) {
        DetectModerationLabelsResponse.Builder result = DetectModerationLabelsResponse.builder();
        ModerationLabel label = ModerationLabel.builder()
                .name("TestModerationLabel")
                .confidence(90.0f)
                .build();
        result.moderationLabels(Collections.singletonList(label));
        return result.build();
    }

    @Override
    public DetectProtectiveEquipmentResponse detectProtectiveEquipment(
            DetectProtectiveEquipmentRequest detectProtectiveEquipmentRequest) {
        DetectProtectiveEquipmentResponse.Builder result = DetectProtectiveEquipmentResponse.builder();
        result.protectiveEquipmentModelVersion("1.0");
        return result.build();
    }

    @Override
    public DetectTextResponse detectText(DetectTextRequest detectTextRequest) {
        DetectTextResponse.Builder result = DetectTextResponse.builder();
        TextDetection text = TextDetection.builder()
                .detectedText("TestText")
                .confidence(98.0f)
                .build();
        result.textDetections(Collections.singletonList(text));
        return result.build();
    }

    @Override
    public DisassociateFacesResponse disassociateFaces(DisassociateFacesRequest disassociateFacesRequest) {
        DisassociateFacesResponse.Builder result = DisassociateFacesResponse.builder();
        DisassociatedFace face = DisassociatedFace.builder()
                .faceId("test-face-id")
                .build();
        result.disassociatedFaces(Collections.singletonList(face));
        return result.build();
    }

    @Override
    public GetCelebrityInfoResponse getCelebrityInfo(GetCelebrityInfoRequest getCelebrityInfoRequest) {
        GetCelebrityInfoResponse.Builder result = GetCelebrityInfoResponse.builder();
        result.name("TestCelebrity");
        return result.build();
    }

    @Override
    public GetMediaAnalysisJobResponse getMediaAnalysisJob(GetMediaAnalysisJobRequest getMediaAnalysisJobRequest) {
        GetMediaAnalysisJobResponse.Builder result = GetMediaAnalysisJobResponse.builder();
        result.jobId("test-job-id");
        result.status(MediaAnalysisJobStatus.SUCCEEDED);
        return result.build();
    }

    @Override
    public IndexFacesResponse indexFaces(IndexFacesRequest indexFacesRequest) {
        IndexFacesResponse.Builder result = IndexFacesResponse.builder();
        FaceRecord record = FaceRecord.builder()
                .face(Face.builder().faceId("test-face-id").build())
                .build();
        result.faceRecords(Collections.singletonList(record));
        return result.build();
    }

    @Override
    public ListCollectionsResponse listCollections(ListCollectionsRequest listCollectionsRequest) {
        ListCollectionsResponse.Builder result = ListCollectionsResponse.builder();
        List<String> collections = new ArrayList<>();
        collections.add("test-collection");
        result.collectionIds(collections);
        return result.build();
    }

    @Override
    public ListMediaAnalysisJobsResponse listMediaAnalysisJobs(ListMediaAnalysisJobsRequest listMediaAnalysisJobsRequest) {
        ListMediaAnalysisJobsResponse.Builder result = ListMediaAnalysisJobsResponse.builder();
        MediaAnalysisJobDescription job = MediaAnalysisJobDescription.builder()
                .jobId("test-job-id")
                .status(MediaAnalysisJobStatus.SUCCEEDED)
                .build();
        result.mediaAnalysisJobs(Collections.singletonList(job));
        return result.build();
    }

    @Override
    public ListFacesResponse listFaces(ListFacesRequest listFacesRequest) {
        ListFacesResponse.Builder result = ListFacesResponse.builder();
        Face face = Face.builder()
                .faceId("test-face-id")
                .confidence(99.0f)
                .build();
        result.faces(Collections.singletonList(face));
        return result.build();
    }

    @Override
    public ListUsersResponse listUsers(ListUsersRequest listUsersRequest) {
        ListUsersResponse.Builder result = ListUsersResponse.builder();
        User user = User.builder()
                .userId("test-user-id")
                .build();
        result.users(Collections.singletonList(user));
        return result.build();
    }

    @Override
    public RecognizeCelebritiesResponse recognizeCelebrities(RecognizeCelebritiesRequest recognizeCelebritiesRequest) {
        RecognizeCelebritiesResponse.Builder result = RecognizeCelebritiesResponse.builder();
        Celebrity celebrity = Celebrity.builder()
                .name("TestCelebrity")
                .id("celebrity-123")
                .build();
        result.celebrityFaces(Collections.singletonList(celebrity));
        return result.build();
    }

    @Override
    public SearchFacesResponse searchFaces(SearchFacesRequest searchFacesRequest) {
        SearchFacesResponse.Builder result = SearchFacesResponse.builder();
        FaceMatch match = FaceMatch.builder()
                .face(Face.builder().faceId("matched-face-id").build())
                .similarity(95.0f)
                .build();
        result.faceMatches(Collections.singletonList(match));
        return result.build();
    }

    @Override
    public SearchFacesByImageResponse searchFacesByImage(SearchFacesByImageRequest searchFacesByImageRequest) {
        SearchFacesByImageResponse.Builder result = SearchFacesByImageResponse.builder();
        FaceMatch match = FaceMatch.builder()
                .face(Face.builder().faceId("matched-face-id").build())
                .similarity(93.0f)
                .build();
        result.faceMatches(Collections.singletonList(match));
        return result.build();
    }

    @Override
    public SearchUsersResponse searchUsers(SearchUsersRequest searchUsersRequest) {
        SearchUsersResponse.Builder result = SearchUsersResponse.builder();
        UserMatch match = UserMatch.builder()
                .user(MatchedUser.builder().userId("matched-user-id").build())
                .similarity(96.0f)
                .build();
        result.userMatches(Collections.singletonList(match));
        return result.build();
    }

    @Override
    public SearchUsersByImageResponse searchUsersByImage(SearchUsersByImageRequest searchUsersByImageRequest) {
        SearchUsersByImageResponse.Builder result = SearchUsersByImageResponse.builder();
        UserMatch match = UserMatch.builder()
                .user(MatchedUser.builder().userId("matched-user-id").build())
                .similarity(94.0f)
                .build();
        result.userMatches(Collections.singletonList(match));
        return result.build();
    }

    @Override
    public StartMediaAnalysisJobResponse startMediaAnalysisJob(StartMediaAnalysisJobRequest startMediaAnalysisJobRequest) {
        StartMediaAnalysisJobResponse.Builder result = StartMediaAnalysisJobResponse.builder();
        result.jobId("new-job-id");
        return result.build();
    }

    @Override
    public String serviceName() {
        return RekognitionClient.SERVICE_NAME;
    }

    @Override
    public void close() {
    }
}
