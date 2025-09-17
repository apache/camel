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

public interface Transcribe2Constants {

    String TRANSCRIPTION_JOB_NAME = "CamelAwsTranscribeTranscriptionJobName";
    String LANGUAGE_CODE = "CamelAwsTranscribeLanguageCode";
    String MEDIA_FORMAT = "CamelAwsTranscribeMediaFormat";
    String MEDIA_URI = "CamelAwsTranscribeMediaUri";
    String JOB_NAME_CONTAINS = "CamelAwsTranscribeJobNameContains";
    String STATUS = "CamelAwsTranscribeStatus";
    String VOCABULARY_NAME = "CamelAwsTranscribeVocabularyName";
    String VOCABULARY_FILTER_NAME = "CamelAwsTranscribeVocabularyFilterName";
    String VOCABULARY_PHRASES = "CamelAwsTranscribeVocabularyPhrases";
    String LANGUAGE_MODEL_NAME = "CamelAwsTranscribeLanguageModelName";
    String MEDICAL_TRANSCRIPTION_JOB_NAME = "CamelAwsTranscribeMedicalTranscriptionJobName";
    String RESOURCE_ARN = "CamelAwsTranscribeResourceArn";
    String TAGS = "CamelAwsTranscribeTags";
    String TAG_KEYS = "CamelAwsTranscribeTagKeys";
}
