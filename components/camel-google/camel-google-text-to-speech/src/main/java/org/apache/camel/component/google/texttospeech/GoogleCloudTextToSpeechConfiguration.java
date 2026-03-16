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
package org.apache.camel.component.google.texttospeech;

import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.google.common.GoogleCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GoogleCloudTextToSpeechConfiguration implements Cloneable, GoogleCommonConfiguration {

    @UriPath(label = "common", description = "The operation name")
    @Metadata(required = true)
    private String operation;

    @UriParam(label = "common", description = "Service account key to authenticate an application as a service account")
    private String serviceAccountKey;

    @UriParam(label = "producer", defaultValue = "en-US",
              description = "The language code for the voice (e.g., en-US, fr-FR)")
    private String languageCode = "en-US";

    @UriParam(label = "producer",
              description = "The name of the voice. If not set, the service selects a default voice for the specified language")
    private String voiceName;

    @UriParam(label = "producer", defaultValue = "MP3",
              description = "The audio encoding of the output. Supported values: MP3, LINEAR16, OGG_OPUS, MULAW, ALAW")
    private String audioEncoding = "MP3";

    @UriParam(label = "producer",
              description = "The speaking rate, from 0.25 to 4.0. Default is 1.0")
    private Double speakingRate;

    @UriParam(label = "producer",
              description = "The pitch, from -20.0 to 20.0 semitones. Default is 0.0")
    private Double pitch;

    @UriParam(defaultValue = "false", description = "Specifies if the request is a pojo request")
    private boolean pojoRequest;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private TextToSpeechClient client;

    public String getOperation() {
        return operation;
    }

    /**
     * Set the operation name
     *
     * @param operation
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    @Override
    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    /**
     * Service account key
     *
     * @param serviceAccountKey
     */
    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    /**
     * The language code for the voice
     *
     * @param languageCode
     */
    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public String getVoiceName() {
        return voiceName;
    }

    /**
     * The name of the voice
     *
     * @param voiceName
     */
    public void setVoiceName(String voiceName) {
        this.voiceName = voiceName;
    }

    public String getAudioEncoding() {
        return audioEncoding;
    }

    /**
     * The audio encoding of the output
     *
     * @param audioEncoding
     */
    public void setAudioEncoding(String audioEncoding) {
        this.audioEncoding = audioEncoding;
    }

    public Double getSpeakingRate() {
        return speakingRate;
    }

    /**
     * The speaking rate
     *
     * @param speakingRate
     */
    public void setSpeakingRate(Double speakingRate) {
        this.speakingRate = speakingRate;
    }

    public Double getPitch() {
        return pitch;
    }

    /**
     * The pitch
     *
     * @param pitch
     */
    public void setPitch(Double pitch) {
        this.pitch = pitch;
    }

    public TextToSpeechClient getClient() {
        return client;
    }

    /**
     * The client to use during service invocation.
     *
     * @param client
     */
    public void setClient(TextToSpeechClient client) {
        this.client = client;
    }

    public boolean isPojoRequest() {
        return pojoRequest;
    }

    /**
     * Configure the input type. If true the message will be POJO type.
     *
     * @param pojoRequest
     */
    public void setPojoRequest(boolean pojoRequest) {
        this.pojoRequest = pojoRequest;
    }

    public GoogleCloudTextToSpeechConfiguration copy() {
        try {
            return (GoogleCloudTextToSpeechConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
