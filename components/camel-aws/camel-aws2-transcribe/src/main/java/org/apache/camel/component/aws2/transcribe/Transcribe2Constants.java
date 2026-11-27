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

    @Metadata(label = "producer",
              description = "The name of the transcription job, for startTranscriptionJob, getTranscriptionJob and"
                            + " deleteTranscriptionJob. It must be unique in the AWS account.",
              javaType = "String")
    String TRANSCRIPTION_JOB_NAME = "CamelAwsTranscribeTranscriptionJobName";
    @Metadata(label = "producer",
              description = "The language code of the audio, such as en-US, for startTranscriptionJob,"
                            + " startMedicalTranscriptionJob, createVocabulary, updateVocabulary, createMedicalVocabulary,"
                            + " updateMedicalVocabulary, createVocabularyFilter and createLanguageModel. Medical"
                            + " transcription supports only en-US.",
              javaType = "String")
    String LANGUAGE_CODE = "CamelAwsTranscribeLanguageCode";
    @Metadata(label = "producer",
              description = "The format of the media file, such as mp3, wav or flac, for startTranscriptionJob and"
                            + " startMedicalTranscriptionJob. If not set, Amazon Transcribe detects the format.",
              javaType = "String")
    String MEDIA_FORMAT = "CamelAwsTranscribeMediaFormat";
    @Metadata(label = "producer",
              description = "The S3 location of the media file to transcribe, such as s3://my-bucket/audio.mp3, for"
                            + " startTranscriptionJob and startMedicalTranscriptionJob. Amazon Transcribe reads the file"
                            + " from S3, so the message body is not used.",
              javaType = "String")
    String MEDIA_URI = "CamelAwsTranscribeMediaUri";
    @Metadata(label = "producer",
              description = "A text that the names must contain, without regard to case, to filter the jobs of"
                            + " listTranscriptionJobs and listMedicalTranscriptionJobs, and the items of"
                            + " listVocabularyFilters, listLanguageModels and listMedicalVocabularies. listVocabularies"
                            + " does not read it.",
              javaType = "String")
    String JOB_NAME_CONTAINS = "CamelAwsTranscribeJobNameContains";
    @Metadata(label = "producer",
              description = "The status to filter the list by: a job status such as QUEUED, IN_PROGRESS, FAILED or"
                            + " COMPLETED for listTranscriptionJobs and listMedicalTranscriptionJobs, a vocabulary state"
                            + " such as PENDING, READY or FAILED for listVocabularies and listMedicalVocabularies, or a"
                            + " model status such as IN_PROGRESS, FAILED or COMPLETED for listLanguageModels. If not set,"
                            + " every status is listed.",
              javaType = "String")
    String STATUS = "CamelAwsTranscribeStatus";
    @Metadata(label = "producer",
              description = "The name of the custom vocabulary, for createVocabulary, getVocabulary, updateVocabulary"
                            + " and deleteVocabulary, or of the custom medical vocabulary, for createMedicalVocabulary,"
                            + " getMedicalVocabulary, updateMedicalVocabulary and deleteMedicalVocabulary. The"
                            + " transcription jobs do not read it.",
              javaType = "String")
    String VOCABULARY_NAME = "CamelAwsTranscribeVocabularyName";
    @Metadata(label = "producer",
              description = "The name of the custom vocabulary filter, for createVocabularyFilter, getVocabularyFilter,"
                            + " updateVocabularyFilter and deleteVocabularyFilter. The transcription jobs do not read it.",
              javaType = "String")
    String VOCABULARY_FILTER_NAME = "CamelAwsTranscribeVocabularyFilterName";
    @Metadata(label = "producer",
              description = "The phrases of the custom vocabulary, for createVocabulary and updateVocabulary, or the"
                            + " words of the vocabulary filter, for createVocabularyFilter and updateVocabularyFilter, as a"
                            + " List of String, or as a String for a single phrase. Use it or the"
                            + " CamelAwsTranscribeVocabularyFileUri header, not both.",
              javaType = "List<String>")
    String VOCABULARY_PHRASES = "CamelAwsTranscribeVocabularyPhrases";
    @Metadata(label = "producer",
              description = "The name of the custom language model, for createLanguageModel, describeLanguageModel and"
                            + " deleteLanguageModel. The transcription jobs do not read it.",
              javaType = "String")
    String LANGUAGE_MODEL_NAME = "CamelAwsTranscribeLanguageModelName";
    @Metadata(label = "producer",
              description = "The name of the medical transcription job, for startMedicalTranscriptionJob,"
                            + " getMedicalTranscriptionJob and deleteMedicalTranscriptionJob. It must be unique in the AWS"
                            + " account.",
              javaType = "String")
    String MEDICAL_TRANSCRIPTION_JOB_NAME = "CamelAwsTranscribeMedicalTranscriptionJobName";
    @Metadata(label = "producer",
              description = "The Amazon Resource Name (ARN) of the resource, such as a transcription job, a vocabulary or"
                            + " a language model, for tagResource, untagResource and listTagsForResource.",
              javaType = "String")
    String RESOURCE_ARN = "CamelAwsTranscribeResourceArn";
    @Metadata(label = "producer",
              description = "The tags that tagResource adds to the resource, as a Map of tag key to tag value.",
              javaType = "Map<String, String>")
    String TAGS = "CamelAwsTranscribeTags";
    @Metadata(label = "producer",
              description = "The keys of the tags that untagResource removes from the resource, as a List of String.",
              javaType = "List<String>")
    String TAG_KEYS = "CamelAwsTranscribeTagKeys";
    @Metadata(label = "producer",
              description = "The maximum number of items in one page of the result, for listVocabularyFilters,"
                            + " listLanguageModels, listMedicalVocabularies and listMedicalTranscriptionJobs. If not set,"
                            + " Amazon Transcribe returns at most 5. listTranscriptionJobs and listVocabularies do not read"
                            + " it.",
              javaType = "Integer")
    String MAX_RESULTS = "CamelAwsTranscribeMaxResults";
    @Metadata(label = "producer",
              description = "The NextToken of the previous response of the same list operation, to get the next page, for"
                            + " listVocabularyFilters, listLanguageModels, listMedicalVocabularies and"
                            + " listMedicalTranscriptionJobs. listTranscriptionJobs and listVocabularies do not read it.",
              javaType = "String")
    String NEXT_TOKEN = "CamelAwsTranscribeNextToken";
    @Metadata(label = "producer",
              description = "The S3 location of the text file with the terms of the vocabulary, for createVocabulary,"
                            + " updateVocabulary, createMedicalVocabulary and updateMedicalVocabulary, or with the words of"
                            + " the filter, for createVocabularyFilter and updateVocabularyFilter.",
              javaType = "String")
    String VOCABULARY_FILE_URI = "CamelAwsTranscribeVocabularyFileUri";
    @Metadata(label = "producer",
              description = "The base model of the custom language model, for createLanguageModel: NarrowBand for audio"
                            + " with a sample rate below 16,000 Hz, or WideBand for 16,000 Hz and above.",
              javaType = "String")
    String BASE_MODEL_NAME = "CamelAwsTranscribeBaseModelName";
    @Metadata(label = "producer",
              description = "The S3 location of the training data of the custom language model, for"
                            + " createLanguageModel. It is used together with the CamelAwsTranscribeDataAccessRoleArn"
                            + " header.",
              javaType = "String")
    String INPUT_DATA_S3_URI = "CamelAwsTranscribeInputDataS3Uri";
    @Metadata(label = "producer",
              description = "The ARN of the IAM role that Amazon Transcribe uses to read the training data, for"
                            + " createLanguageModel, or the file of the vocabulary filter, for createVocabularyFilter and"
                            + " updateVocabularyFilter.",
              javaType = "String")
    String DATA_ACCESS_ROLE_ARN = "CamelAwsTranscribeDataAccessRoleArn";
    @Metadata(label = "producer",
              description = "The S3 bucket that startMedicalTranscriptionJob writes the transcript to."
                            + " startTranscriptionJob does not read it: Amazon Transcribe then keeps the transcript in a"
                            + " bucket of its own, at the URI that getTranscriptionJob gives.",
              javaType = "String")
    String OUTPUT_BUCKET_NAME = "CamelAwsTranscribeOutputBucketName";
    @Metadata(label = "producer",
              description = "The medical specialty of the audio, for startMedicalTranscriptionJob, such as PRIMARYCARE.",
              javaType = "String")
    String SPECIALTY = "CamelAwsTranscribeSpecialty";
    @Metadata(label = "producer",
              description = "The type of the audio, for startMedicalTranscriptionJob: CONVERSATION for a conversation"
                            + " between people, or DICTATION for a single speaker.",
              javaType = "String")
    String TYPE = "CamelAwsTranscribeType";
}
