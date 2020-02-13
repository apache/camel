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
package org.apache.camel.component.aws.translate;

import java.util.Collection;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.translate.AmazonTranslate;
import com.amazonaws.services.translate.model.TranslateTextRequest;
import com.amazonaws.services.translate.model.TranslateTextResult;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Amazon Translate Service
 * <a href="http://aws.amazon.com/translate/">AWS Translate</a>
 */
public class TranslateProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(TranslateProducer.class);

    private transient String translateProducerToString;

    public TranslateProducer(Endpoint endpoint) {
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

    private TranslateOperations determineOperation(Exchange exchange) {
        TranslateOperations operation = exchange.getIn().getHeader(TranslateConstants.OPERATION, TranslateOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected TranslateConfiguration getConfiguration() {
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
    public TranslateEndpoint getEndpoint() {
        return (TranslateEndpoint)super.getEndpoint();
    }

    private void translateText(AmazonTranslate translateClient, Exchange exchange) {
        TranslateTextRequest request = new TranslateTextRequest();
        if (!getConfiguration().isAutodetectSourceLanguage()) {
            if (ObjectHelper.isEmpty(getConfiguration().getSourceLanguage()) && ObjectHelper.isEmpty(getConfiguration().getTargetLanguage())) {
                String source = exchange.getIn().getHeader(TranslateConstants.SOURCE_LANGUAGE, String.class);
                String target = exchange.getIn().getHeader(TranslateConstants.TARGET_LANGUAGE, String.class);
                if (ObjectHelper.isEmpty(source) || ObjectHelper.isEmpty(target)) {
                    throw new IllegalArgumentException("Source and target language must be specified as headers or endpoint options");
                }
                request.setSourceLanguageCode(source);
                request.setTargetLanguageCode(target);
            } else {
                request.setSourceLanguageCode(getConfiguration().getSourceLanguage());
                request.setTargetLanguageCode(getConfiguration().getTargetLanguage());
            }
        } else {
            String source = "auto";
            if (ObjectHelper.isEmpty(getConfiguration().getTargetLanguage())) {
                String target = exchange.getIn().getHeader(TranslateConstants.TARGET_LANGUAGE, String.class);
                if (ObjectHelper.isEmpty(source) || ObjectHelper.isEmpty(target)) {
                    throw new IllegalArgumentException("Target language must be specified when autodetection of source language is enabled");
                }
                request.setSourceLanguageCode(source);
                request.setTargetLanguageCode(target);
            } else {
                request.setSourceLanguageCode(source);
                request.setTargetLanguageCode(getConfiguration().getTargetLanguage());
            }
        }
        if (!ObjectHelper.isEmpty(exchange.getIn().getHeader(TranslateConstants.TERMINOLOGY_NAMES, Collection.class))) {
            Collection<String> terminologies = exchange.getIn().getHeader(TranslateConstants.TERMINOLOGY_NAMES, Collection.class);
            request.setTerminologyNames(terminologies);
        }
        request.setText(exchange.getMessage().getBody(String.class));
        TranslateTextResult result;
        try {
            result = translateClient.translateText(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Translate Text command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result.getTranslatedText());
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
