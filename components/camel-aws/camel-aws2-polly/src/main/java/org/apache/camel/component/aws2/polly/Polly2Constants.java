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
package org.apache.camel.component.aws2.polly;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS2 Polly module
 */
public interface Polly2Constants {
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsPollyOperation";
    @Metadata(description = "The voice ID to use for synthesis",
              javaType = "software.amazon.awssdk.services.polly.model.VoiceId")
    String VOICE_ID = "CamelAwsPollyVoiceId";
    @Metadata(description = "The output format for the audio stream",
              javaType = "software.amazon.awssdk.services.polly.model.OutputFormat")
    String OUTPUT_FORMAT = "CamelAwsPollyOutputFormat";
    @Metadata(description = "The type of text input (text or ssml)",
              javaType = "software.amazon.awssdk.services.polly.model.TextType")
    String TEXT_TYPE = "CamelAwsPollyTextType";
    @Metadata(description = "The sample rate in Hz", javaType = "String")
    String SAMPLE_RATE = "CamelAwsPollySampleRate";
    @Metadata(description = "The engine to use (standard, neural, long-form, generative)",
              javaType = "software.amazon.awssdk.services.polly.model.Engine")
    String ENGINE = "CamelAwsPollyEngine";
    @Metadata(description = "The language code", javaType = "String")
    String LANGUAGE_CODE = "CamelAwsPollyLanguageCode";
    @Metadata(description = "Comma-separated list of lexicon names", javaType = "String")
    String LEXICON_NAMES = "CamelAwsPollyLexiconNames";
    @Metadata(description = "The name of the lexicon", javaType = "String")
    String LEXICON_NAME = "CamelAwsPollyLexiconName";
    @Metadata(description = "The content of the lexicon in PLS format", javaType = "String")
    String LEXICON_CONTENT = "CamelAwsPollyLexiconContent";
    @Metadata(description = "The task ID for speech synthesis task", javaType = "String")
    String TASK_ID = "CamelAwsPollyTaskId";
    @Metadata(description = "The S3 bucket name for output", javaType = "String")
    String S3_BUCKET = "CamelAwsPollyS3Bucket";
    @Metadata(description = "The S3 key prefix for output", javaType = "String")
    String S3_KEY_PREFIX = "CamelAwsPollyS3KeyPrefix";
    @Metadata(description = "The SNS topic ARN for notifications", javaType = "String")
    String SNS_TOPIC_ARN = "CamelAwsPollySnsTopicArn";
    @Metadata(description = "The content type of the audio stream", javaType = "String")
    String CONTENT_TYPE = "CamelAwsPollyContentType";
    @Metadata(description = "Number of characters synthesized", javaType = "Integer")
    String REQUEST_CHARACTERS = "CamelAwsPollyRequestCharacters";
}
