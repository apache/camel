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
package org.apache.camel.component.aws2.comprehend;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel AWS2 Comprehend module
 */
public interface Comprehend2Constants {
    @Metadata(description = "The operation we want to perform", javaType = "String")
    String OPERATION = "CamelAwsComprehendOperation";
    @Metadata(description = "The language code of the input text", javaType = "String")
    String LANGUAGE_CODE = "CamelAwsComprehendLanguageCode";
    @Metadata(description = "The Amazon Resource Name (ARN) of the endpoint to use for document classification",
              javaType = "String")
    String ENDPOINT_ARN = "CamelAwsComprehendEndpointArn";
    @Metadata(description = "The detected dominant language", javaType = "String")
    String DETECTED_LANGUAGE = "CamelAwsComprehendDetectedLanguage";
    @Metadata(description = "The detected dominant language score", javaType = "Float")
    String DETECTED_LANGUAGE_SCORE = "CamelAwsComprehendDetectedLanguageScore";
    @Metadata(description = "The detected sentiment", javaType = "String")
    String DETECTED_SENTIMENT = "CamelAwsComprehendDetectedSentiment";
    @Metadata(description = "The detected sentiment scores",
              javaType = "software.amazon.awssdk.services.comprehend.model.SentimentScore")
    String DETECTED_SENTIMENT_SCORE = "CamelAwsComprehendDetectedSentimentScore";
}
