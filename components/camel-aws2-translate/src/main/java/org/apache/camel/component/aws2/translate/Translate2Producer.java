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
package org.apache.camel.component.aws2.translate;

import java.util.Collection;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.translate.TranslateClient;
import software.amazon.awssdk.services.translate.model.TranslateTextRequest;
import software.amazon.awssdk.services.translate.model.TranslateTextRequest.Builder;
import software.amazon.awssdk.services.translate.model.TranslateTextResponse;

/**
 * A Producer which sends messages to the Amazon Translate Service SDK v2
 * <a href="http://aws.amazon.com/translate/">AWS Translate</a>
 */
public class Translate2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Translate2Producer.class);
    private transient String translateProducerToString;

    public Translate2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case translateText:
                translateText(getEndpoint().getTranslateClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private Translate2Operations determineOperation(Exchange exchange) {
        Translate2Operations operation = exchange.getIn().getHeader(Translate2Constants.OPERATION, Translate2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected Translate2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (translateProducerToString == null) {
            translateProducerToString = "TranslateProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return translateProducerToString;
    }

    @Override
    public Translate2Endpoint getEndpoint() {
        return (Translate2Endpoint)super.getEndpoint();
    }

    private void translateText(TranslateClient translateClient, Exchange exchange) {
        Builder request = TranslateTextRequest.builder();
        if (!getConfiguration().isAutodetectSourceLanguage()) {
            if (ObjectHelper.isEmpty(getConfiguration().getSourceLanguage()) && ObjectHelper.isEmpty(getConfiguration().getTargetLanguage())) {
                String source = exchange.getIn().getHeader(Translate2Constants.SOURCE_LANGUAGE, String.class);
                String target = exchange.getIn().getHeader(Translate2Constants.TARGET_LANGUAGE, String.class);
                if (ObjectHelper.isEmpty(source) || ObjectHelper.isEmpty(target)) {
                    throw new IllegalArgumentException("Source and target language must be specified as headers or endpoint options");
                }
                request.sourceLanguageCode(source);
                request.targetLanguageCode(target);
            } else {
                request.sourceLanguageCode(getConfiguration().getSourceLanguage());
                request.targetLanguageCode(getConfiguration().getTargetLanguage());
            }
        } else {
            String source = "auto";
            if (ObjectHelper.isEmpty(getConfiguration().getTargetLanguage())) {
                String target = exchange.getIn().getHeader(Translate2Constants.TARGET_LANGUAGE, String.class);
                if (ObjectHelper.isEmpty(source) || ObjectHelper.isEmpty(target)) {
                    throw new IllegalArgumentException("Target language must be specified when autodetection of source language is enabled");
                }
                request.sourceLanguageCode(source);
                request.targetLanguageCode(target);
            } else {
                request.sourceLanguageCode(source);
                request.targetLanguageCode(getConfiguration().getTargetLanguage());
            }
        }
        if (!ObjectHelper.isEmpty(exchange.getIn().getHeader(Translate2Constants.TERMINOLOGY_NAMES, Collection.class))) {
            Collection<String> terminologies = exchange.getIn().getHeader(Translate2Constants.TERMINOLOGY_NAMES, Collection.class);
            request.terminologyNames(terminologies);
        }
        request.text(exchange.getMessage().getBody(String.class));
        TranslateTextResponse result;
        try {
            result = translateClient.translateText(request.build());
        } catch (AwsServiceException ase) {
            LOG.trace("Translate Text command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result.translatedText());
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
