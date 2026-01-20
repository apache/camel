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

import org.apache.camel.spi.Metadata;

public interface Rekognition2Constants {

    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsRekognitionOperation";

    @Metadata(description = "The ID of the Collection", javaType = "String")
    String COLLECTION_ID = "CamelAwsRekognitionCollectionId";

    @Metadata(description = "The ID of the User", javaType = "String")
    String USER_ID = "CamelAwsRekognitionUserId";

    @Metadata(description = "Collection of the Face IDs", javaType = "Collection<String>")
    String FACE_IDS = "CamelAwsRekognitionFaceIds";

    @Metadata(description = "Minimum user match confidence required for the face to be associated with a UserID " +
                            "that has at least one FaceID already associated",
              javaType = "Float")
    String USER_MATCH_THRESHOLD = "CamelAwsRekognitionUserMatchThreshold";

    @Metadata(description = "The ID of the User", javaType = "String")
    String CLIENT_REQUEST_TOKEN = "CamelAwsRekognitionClientRequestToken";

    @Metadata(description = "Source Input Image", javaType = "software.amazon.awssdk.services.rekognition.model.Image")
    String SOURCE_IMAGE = "CamelAwsRekognitionSourceImage";

    @Metadata(description = "Target Input Image", javaType = "software.amazon.awssdk.services.rekognition.model.Image")
    String TARGET_IMAGE = "CamelAwsRekognitionTargetImage";

    @Metadata(description = "Similarity Score Threshold", javaType = "Float")
    String SIMILARITY_THRESHOLD = "CamelAwsRekognitionSimilarityThreshold";

    @Metadata(description = "Allows to filter out detected faces that don’t meet a required quality bar.", javaType = "String")
    String QUALITY_FILTER = "CamelAwsRekognitionQualityFilter";

    @Metadata(description = "Input Image", javaType = "software.amazon.awssdk.services.rekognition.model.Image")
    String IMAGE = "CamelAwsRekognitionImage";

    @Metadata(description = "Facial Attributes to be returned",
              javaType = "List<oftware.amazon.awssdk.services.rekognition.model.Attribute>")
    String FACIAL_ATTRIBUTES = "CamelAwsRekognitionFacialAttributes";

    @Metadata(description = "Maximum Labels to detect", javaType = "Integer")
    String MAX_LABELS = "CamelAwsRekognitionMaxLabels";

    @Metadata(description = "Minimum confidence level for the labels to return", javaType = "Float")
    String MIN_CONFIDENCE = "CamelAwsRekognitionMinConfidence";

    @Metadata(description = "A list of the types of analysis to perform",
              javaType = "Collection<software.amazon.awssdk.services.rekognition.model.DetectLabelsFeatureName>")
    String FEATURES = "CamelAwsRekognitionFeatures";

    @Metadata(description = "A list of the filters to be applied to returned detected labels and image properties",
              javaType = "software.amazon.awssdk.services.rekognition.model.DetectLabelsSettings")
    String DETECT_LABELS_SETTINGS = "CamelAwsRekognitionDetectLabelsSettings";

    @Metadata(description = "Sets up the configuration for human evaluation",
              javaType = "software.amazon.awssdk.services.rekognition.model.HumanLoopConfig")
    String HUMAN_LOOP_CONFIG = "CamelAwsRekognitionHumanLoopConfig";

    @Metadata(description = "Identifier for the custom adapter", javaType = "String")
    String PROJECT_VERSION = "CamelAwsRekognitionProjectVersion";

    @Metadata(description = "An array of PPE types to summarize.",
              javaType = "software.amazon.awssdk.services.rekognition.model.ProtectiveEquipmentSummarizationAttributes")
    String PROTECTIVE_EQUIPMENT_SUMMARIZATION_ATTRIBUTES = "CamelAwsRekognitionProtectiveEquipmentSummarizationAttributes";

    @Metadata(description = "An optional filter that specifies words to include in the response",
              javaType = "software.amazon.awssdk.services.rekognition.model.DetectTextFilters")
    String WORD_FILTER = "CamelAwsRekognitionWordFilter";

    @Metadata(description = "The ID of the celebrity to get information about", javaType = "String")
    String CELEBRITY_ID = "CamelAwsRekognitionCelebrityId";

    @Metadata(description = "Unique identifier for the media analysis job for which you want to retrieve results",
              javaType = "String")
    String JOB_ID = "CamelAwsRekognitionJobId";

    @Metadata(description = "The name of the media analysis job", javaType = "String")
    String JOB_NAME = "CamelAwsRekognitionJobName";

    @Metadata(description = "Input data to be analyzed by the job",
              javaType = "software.amazon.awssdk.services.rekognition.model.MediaAnalysisInput")
    String INPUT = "CamelAwsRekognitionInput";

    @Metadata(description = "The Amazon S3 bucket location to store the results",
              javaType = "software.amazon.awssdk.services.rekognition.model.MediaAnalysisOutputConfig")
    String OUTPUT_CONFIG = "CamelAwsRekognitionOutputConfig";

    @Metadata(description = "Configuration options for the media analysis job to be created",
              javaType = "software.amazon.awssdk.services.rekognition.model.MediaAnalysisOperationsConfig")
    String OPERATIONS_CONFIG = "CamelAwsRekognitionOperationsConfig";

    @Metadata(description = "The identifier for your AWS Key Management Service key (AWS KMS key)", javaType = "String")
    String KMS_KEY_ID = "CamelAwsRekognitionKmsKeyId";

    @Metadata(description = "The ID used to identify a user in the collection", javaType = "String")
    String EXTERNAL_IMAGE_ID = "CamelAwsRekognitionExternalImageId";

    @Metadata(description = "An array of facial attributes to return", javaType = "Collection<String>")
    String DETECTION_ATTRIBUTES = "CamelAwsRekognitionDetectionAttributes";

    @Metadata(description = "The maximum number of faces to index", javaType = "Integer")
    String MAX_FACES = "CamelAwsRekognitionMaxFaces";

    @Metadata(description = "Maximum number of results to return", javaType = "Integer")
    String MAX_RESULTS = "CamelAwsRekognitionMaxResults";

    @Metadata(description = "Pagination token from the previous response", javaType = "String")
    String NEXT_TOKEN = "CamelAwsRekognitionNextToken";

    @Metadata(description = "ID of a face to find matches for in the collection", javaType = "String")
    String FACE_ID = "CamelAwsRekognitionFaceId";

    @Metadata(description = "Optional value specifying the minimum confidence in the face match to return", javaType = "Float")
    String FACE_MATCH_THRESHOLD = "CamelAwsRekognitionFaceMatchThreshold";

    @Metadata(description = "Maximum number of users to return in the response", javaType = "Integer")
    String MAX_USERS = "CamelAwsRekognitionMaxUsers";

}
