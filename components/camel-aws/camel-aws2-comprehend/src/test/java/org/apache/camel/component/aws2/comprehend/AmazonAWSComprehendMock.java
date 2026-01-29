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

import java.util.List;

import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.ComprehendServiceClientConfiguration;
import software.amazon.awssdk.services.comprehend.model.ContainsPiiEntitiesRequest;
import software.amazon.awssdk.services.comprehend.model.ContainsPiiEntitiesResponse;
import software.amazon.awssdk.services.comprehend.model.DetectDominantLanguageRequest;
import software.amazon.awssdk.services.comprehend.model.DetectDominantLanguageResponse;
import software.amazon.awssdk.services.comprehend.model.DetectEntitiesRequest;
import software.amazon.awssdk.services.comprehend.model.DetectEntitiesResponse;
import software.amazon.awssdk.services.comprehend.model.DetectKeyPhrasesRequest;
import software.amazon.awssdk.services.comprehend.model.DetectKeyPhrasesResponse;
import software.amazon.awssdk.services.comprehend.model.DetectPiiEntitiesRequest;
import software.amazon.awssdk.services.comprehend.model.DetectPiiEntitiesResponse;
import software.amazon.awssdk.services.comprehend.model.DetectSentimentRequest;
import software.amazon.awssdk.services.comprehend.model.DetectSentimentResponse;
import software.amazon.awssdk.services.comprehend.model.DetectSyntaxRequest;
import software.amazon.awssdk.services.comprehend.model.DetectSyntaxResponse;
import software.amazon.awssdk.services.comprehend.model.DetectToxicContentRequest;
import software.amazon.awssdk.services.comprehend.model.DetectToxicContentResponse;
import software.amazon.awssdk.services.comprehend.model.DominantLanguage;
import software.amazon.awssdk.services.comprehend.model.Entity;
import software.amazon.awssdk.services.comprehend.model.EntityLabel;
import software.amazon.awssdk.services.comprehend.model.EntityType;
import software.amazon.awssdk.services.comprehend.model.KeyPhrase;
import software.amazon.awssdk.services.comprehend.model.PartOfSpeechTag;
import software.amazon.awssdk.services.comprehend.model.PartOfSpeechTagType;
import software.amazon.awssdk.services.comprehend.model.PiiEntity;
import software.amazon.awssdk.services.comprehend.model.PiiEntityType;
import software.amazon.awssdk.services.comprehend.model.SentimentScore;
import software.amazon.awssdk.services.comprehend.model.SentimentType;
import software.amazon.awssdk.services.comprehend.model.SyntaxToken;
import software.amazon.awssdk.services.comprehend.model.ToxicContent;
import software.amazon.awssdk.services.comprehend.model.ToxicLabels;

public class AmazonAWSComprehendMock implements ComprehendClient {

    @Override
    public DetectDominantLanguageResponse detectDominantLanguage(DetectDominantLanguageRequest request) {
        return DetectDominantLanguageResponse.builder()
                .languages(List.of(
                        DominantLanguage.builder().languageCode("en").score(0.99f).build(),
                        DominantLanguage.builder().languageCode("es").score(0.01f).build()))
                .build();
    }

    @Override
    public DetectEntitiesResponse detectEntities(DetectEntitiesRequest request) {
        return DetectEntitiesResponse.builder()
                .entities(List.of(
                        Entity.builder().text("John Doe").type(EntityType.PERSON).score(0.98f).beginOffset(0).endOffset(8)
                                .build(),
                        Entity.builder().text("New York").type(EntityType.LOCATION).score(0.95f).beginOffset(18).endOffset(26)
                                .build()))
                .build();
    }

    @Override
    public DetectKeyPhrasesResponse detectKeyPhrases(DetectKeyPhrasesRequest request) {
        return DetectKeyPhrasesResponse.builder()
                .keyPhrases(List.of(
                        KeyPhrase.builder().text("important meeting").score(0.99f).beginOffset(0).endOffset(17).build(),
                        KeyPhrase.builder().text("next week").score(0.95f).beginOffset(18).endOffset(27).build()))
                .build();
    }

    @Override
    public DetectSentimentResponse detectSentiment(DetectSentimentRequest request) {
        return DetectSentimentResponse.builder()
                .sentiment(SentimentType.POSITIVE)
                .sentimentScore(SentimentScore.builder()
                        .positive(0.85f)
                        .negative(0.05f)
                        .neutral(0.08f)
                        .mixed(0.02f)
                        .build())
                .build();
    }

    @Override
    public DetectSyntaxResponse detectSyntax(DetectSyntaxRequest request) {
        return DetectSyntaxResponse.builder()
                .syntaxTokens(List.of(
                        SyntaxToken.builder().text("The").tokenId(1)
                                .partOfSpeech(PartOfSpeechTag.builder().tag(PartOfSpeechTagType.DET).score(0.99f).build())
                                .build(),
                        SyntaxToken.builder().text("quick").tokenId(2)
                                .partOfSpeech(PartOfSpeechTag.builder().tag(PartOfSpeechTagType.ADJ).score(0.98f).build())
                                .build()))
                .build();
    }

    @Override
    public DetectPiiEntitiesResponse detectPiiEntities(DetectPiiEntitiesRequest request) {
        return DetectPiiEntitiesResponse.builder()
                .entities(List.of(
                        PiiEntity.builder().type(PiiEntityType.EMAIL).score(0.99f).beginOffset(10).endOffset(25).build(),
                        PiiEntity.builder().type(PiiEntityType.PHONE).score(0.95f).beginOffset(30).endOffset(42).build()))
                .build();
    }

    @Override
    public DetectToxicContentResponse detectToxicContent(DetectToxicContentRequest request) {
        return DetectToxicContentResponse.builder()
                .resultList(List.of(
                        ToxicLabels.builder()
                                .labels(List.of(
                                        ToxicContent.builder().name("PROFANITY").score(0.01f).build(),
                                        ToxicContent.builder().name("HATE_SPEECH").score(0.02f).build()))
                                .toxicity(0.015f)
                                .build()))
                .build();
    }

    @Override
    public ContainsPiiEntitiesResponse containsPiiEntities(ContainsPiiEntitiesRequest request) {
        return ContainsPiiEntitiesResponse.builder()
                .labels(List.of(
                        EntityLabel.builder().name(PiiEntityType.EMAIL).score(0.99f).build(),
                        EntityLabel.builder().name(PiiEntityType.SSN).score(0.85f).build()))
                .build();
    }

    @Override
    public ComprehendServiceClientConfiguration serviceClientConfiguration() {
        return null;
    }

    @Override
    public String serviceName() {
        return "comprehend";
    }

    @Override
    public void close() {
    }
}
