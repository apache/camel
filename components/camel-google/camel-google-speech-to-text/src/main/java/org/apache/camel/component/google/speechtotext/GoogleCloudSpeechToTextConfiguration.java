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
package org.apache.camel.component.google.speechtotext;

import com.google.cloud.speech.v1.SpeechClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.google.common.GoogleCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GoogleCloudSpeechToTextConfiguration implements Cloneable, GoogleCommonConfiguration {

    @UriPath(label = "common", description = "The operation name")
    @Metadata(required = true)
    private String operation;

    @UriParam(label = "common", description = "Service account key to authenticate an application as a service account")
    private String serviceAccountKey;

    @UriParam(defaultValue = "LINEAR16", label = "producer",
              description = "The encoding of the audio data. Supported values: LINEAR16, FLAC, MULAW, AMR, AMR_WB, OGG_OPUS, SPEEX_WITH_HEADER_BYTE, WEBM_OPUS, MP3")
    private String encoding = "LINEAR16";

    @UriParam(label = "producer",
              description = "The sample rate in Hertz of the audio data. Valid values range from 8000 to 48000")
    private Integer sampleRateHertz;

    @UriParam(defaultValue = "en-US", label = "producer",
              description = "The language of the audio data as a BCP-47 language tag (e.g., en-US, fr-FR)")
    private String languageCode = "en-US";

    @UriParam(defaultValue = "false", description = "Specifies if the request is a pojo request")
    private boolean pojoRequest;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private SpeechClient client;

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

    public String getEncoding() {
        return encoding;
    }

    /**
     * The encoding of the audio data.
     *
     * @param encoding
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public Integer getSampleRateHertz() {
        return sampleRateHertz;
    }

    /**
     * The sample rate in Hertz of the audio data.
     *
     * @param sampleRateHertz
     */
    public void setSampleRateHertz(Integer sampleRateHertz) {
        this.sampleRateHertz = sampleRateHertz;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    /**
     * The language of the audio data as a BCP-47 language tag.
     *
     * @param languageCode
     */
    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public SpeechClient getClient() {
        return client;
    }

    /**
     * The client to use during service invocation.
     *
     * @param client
     */
    public void setClient(SpeechClient client) {
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

    public GoogleCloudSpeechToTextConfiguration copy() {
        try {
            return (GoogleCloudSpeechToTextConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
