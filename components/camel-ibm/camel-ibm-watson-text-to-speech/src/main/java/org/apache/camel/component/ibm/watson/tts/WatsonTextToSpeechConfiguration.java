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

package org.apache.camel.component.ibm.watson.tts;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class WatsonTextToSpeechConfiguration implements Cloneable {

    @UriParam(label = "security", secret = true)
    @Metadata(required = true)
    private String apiKey;

    @UriParam(label = "common")
    private String serviceUrl;

    @UriParam(label = "producer")
    private WatsonTextToSpeechOperations operation;

    @UriParam(label = "producer", defaultValue = "en-US_MichaelV3Voice")
    private String voice = "en-US_MichaelV3Voice";

    @UriParam(label = "producer", defaultValue = "audio/wav")
    private String accept = "audio/wav";

    @UriParam(label = "producer")
    private String customizationId;

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

    public WatsonTextToSpeechOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform
     */
    public void setOperation(WatsonTextToSpeechOperations operation) {
        this.operation = operation;
    }

    public String getVoice() {
        return voice;
    }

    /**
     * The voice to use for synthesis. Default is en-US_MichaelV3Voice. Examples: en-US_AllisonV3Voice,
     * en-GB_KateV3Voice, es-ES_EnriqueV3Voice, fr-FR_NicolasV3Voice
     */
    public void setVoice(String voice) {
        this.voice = voice;
    }

    public String getAccept() {
        return accept;
    }

    /**
     * The audio format for synthesized speech. Default is audio/wav. Supported formats: audio/wav, audio/mp3,
     * audio/ogg, audio/flac, audio/webm
     */
    public void setAccept(String accept) {
        this.accept = accept;
    }

    public String getCustomizationId() {
        return customizationId;
    }

    /**
     * The customization ID (GUID) of a custom voice model to use for synthesis
     */
    public void setCustomizationId(String customizationId) {
        this.customizationId = customizationId;
    }

    public WatsonTextToSpeechConfiguration copy() {
        try {
            return (WatsonTextToSpeechConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
