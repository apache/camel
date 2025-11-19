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
package org.apache.camel.component.ibm.watson.stt;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class WatsonSpeechToTextConfiguration implements Cloneable {

    @UriParam(label = "security", secret = true)
    @Metadata(required = true)
    private String apiKey;

    @UriParam(label = "common")
    private String serviceUrl;

    @UriParam(label = "producer")
    private WatsonSpeechToTextOperations operation;

    @UriParam(label = "producer", defaultValue = "en-US_BroadbandModel")
    private String model = "en-US_BroadbandModel";

    @UriParam(label = "producer", defaultValue = "audio/wav")
    private String contentType = "audio/wav";

    @UriParam(label = "producer", defaultValue = "false")
    private boolean timestamps = false;

    @UriParam(label = "producer", defaultValue = "false")
    private boolean wordConfidence = false;

    @UriParam(label = "producer", defaultValue = "false")
    private boolean speakerLabels = false;

    public String getApiKey() {
        return apiKey;
    }

    /**
     * The IBM Cloud API key for authentication
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    /**
     * The service endpoint URL. If not specified, the default URL will be used.
     */
    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public WatsonSpeechToTextOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform
     */
    public void setOperation(WatsonSpeechToTextOperations operation) {
        this.operation = operation;
    }

    public String getModel() {
        return model;
    }

    /**
     * The language model to use for recognition. Default is en-US_BroadbandModel. Examples: en-US_NarrowbandModel,
     * en-GB_BroadbandModel, es-ES_BroadbandModel, fr-FR_BroadbandModel
     */
    public void setModel(String model) {
        this.model = model;
    }

    public String getContentType() {
        return contentType;
    }

    /**
     * The audio format (MIME type). Default is audio/wav. Supported formats: audio/wav, audio/mp3, audio/flac,
     * audio/ogg, audio/webm
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public boolean isTimestamps() {
        return timestamps;
    }

    /**
     * Whether to include timestamps for each word in the transcription. Default is false.
     */
    public void setTimestamps(boolean timestamps) {
        this.timestamps = timestamps;
    }

    public boolean isWordConfidence() {
        return wordConfidence;
    }

    /**
     * Whether to include confidence scores for each word. Default is false.
     */
    public void setWordConfidence(boolean wordConfidence) {
        this.wordConfidence = wordConfidence;
    }

    public boolean isSpeakerLabels() {
        return speakerLabels;
    }

    /**
     * Whether to identify different speakers in the audio. Default is false.
     */
    public void setSpeakerLabels(boolean speakerLabels) {
        this.speakerLabels = speakerLabels;
    }

    public WatsonSpeechToTextConfiguration copy() {
        try {
            return (WatsonSpeechToTextConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
