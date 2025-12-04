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

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS2 Transcribe module
 */
public interface Transcribe2Constants {

    @Metadata(label = "producer", description = "The name of the transcription job", javaType = "String")
    String TRANSCRIPTION_JOB_NAME = "CamelAwsTranscribeTranscriptionJobName";

    @Metadata(label = "producer", description = "The language code for the transcription job", javaType = "String")
    String LANGUAGE_CODE = "CamelAwsTranscribeLanguageCode";

    @Metadata(label = "producer", description = "The format of the input media file", javaType = "String")
    String MEDIA_FORMAT = "CamelAwsTranscribeMediaFormat";

    @Metadata(label = "producer", description = "The URI of the media file to transcribe", javaType = "String")
    String MEDIA_URI = "CamelAwsTranscribeMediaUri";

    @Metadata(
            label = "producer",
            description = "Filter transcription jobs by name containing this string",
            javaType = "String")
    String JOB_NAME_CONTAINS = "CamelAwsTranscribeJobNameContains";

    @Metadata(label = "producer", description = "The status of the transcription job", javaType = "String")
    String STATUS = "CamelAwsTranscribeStatus";

    @Metadata(label = "producer", description = "The name of the custom vocabulary to use", javaType = "String")
    String VOCABULARY_NAME = "CamelAwsTranscribeVocabularyName";

    @Metadata(label = "producer", description = "The name of the vocabulary filter to use", javaType = "String")
    String VOCABULARY_FILTER_NAME = "CamelAwsTranscribeVocabularyFilterName";

    @Metadata(label = "producer", description = "List of phrases for custom vocabulary", javaType = "List<String>")
    String VOCABULARY_PHRASES = "CamelAwsTranscribeVocabularyPhrases";

    @Metadata(label = "producer", description = "The name of the custom language model to use", javaType = "String")
    String LANGUAGE_MODEL_NAME = "CamelAwsTranscribeLanguageModelName";

    @Metadata(label = "producer", description = "The name of the medical transcription job", javaType = "String")
    String MEDICAL_TRANSCRIPTION_JOB_NAME = "CamelAwsTranscribeMedicalTranscriptionJobName";

    @Metadata(label = "producer", description = "The Amazon Resource Name (ARN) of the resource", javaType = "String")
    String RESOURCE_ARN = "CamelAwsTranscribeResourceArn";

    @Metadata(
            label = "producer",
            description = "A map of tags to assign to the resource",
            javaType = "Map<String, String>")
    String TAGS = "CamelAwsTranscribeTags";

    @Metadata(
            label = "producer",
            description = "A list of tag keys to remove from the resource",
            javaType = "List<String>")
    String TAG_KEYS = "CamelAwsTranscribeTagKeys";
}
