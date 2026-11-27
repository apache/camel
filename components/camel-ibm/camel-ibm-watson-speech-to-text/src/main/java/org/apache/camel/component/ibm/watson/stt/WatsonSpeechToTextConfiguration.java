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

    @UriParam(label = "security", security = "secret",
              description = "The IBM Cloud API key of the Speech to Text service instance. The client gets IAM access tokens"
                            + " with it. The endpoint fails to start without it.")
    @Metadata(required = true)
    private String apiKey;

    @UriParam(label = "producer",
              description = "The URL of the Speech to Text service instance, for example"
                            + " https://api.eu-de.speech-to-text.watson.cloud.ibm.com. If not set, the IBM Watson SDK uses"
                            + " https://api.us-south.speech-to-text.watson.cloud.ibm.com, unless its external configuration,"
                            + " such as the SPEECH_TO_TEXT_URL environment variable, gives a URL.")
    private String serviceUrl;

    @UriParam(label = "producer",
              description = "The operation that the producer performs: recognize transcribes audio, listModels and getModel"
                            + " return the speech models, and listCustomModels and getCustomModel the custom language models."
                            + " The CamelIBMWatsonSTTOperation header replaces this option, and the producer fails if neither"
                            + " is set.")
    private WatsonSpeechToTextOperations operation;

    @UriParam(label = "producer", defaultValue = "en-US_BroadbandModel",
              description = "The speech model that recognize uses, for example en-US_Multimedia or en-GB_Telephony. The"
                            + " sampling rate of the audio must be at least 16 kHz for a broadband or multimedia model and"
                            + " 8 kHz for a narrowband or telephony model. The CamelIBMWatsonSTTModel header replaces this"
                            + " option.")
    private String model = "en-US_BroadbandModel";

    @UriParam(label = "producer", defaultValue = "audio/wav",
              description = "The format (MIME type) of the audio for recognize, for example audio/wav, audio/flac, audio/mp3,"
                            + " audio/ogg or audio/webm. audio/l16, audio/alaw and audio/mulaw also need the sampling rate, as in"
                            + " audio/l16;rate=16000, and application/octet-stream lets the service detect the format. The"
                            + " CamelIBMWatsonSTTContentType header replaces this option.")
    private String contentType = "audio/wav";

    @UriParam(label = "producer", defaultValue = "false",
              description = "Whether recognize returns the start and end time of each word, in seconds. The"
                            + " CamelIBMWatsonSTTTimestamps header replaces this option.")
    private boolean timestamps = false;

    @UriParam(label = "producer", defaultValue = "false",
              description = "Whether recognize returns a confidence score from 0.0 to 1.0 for each word. The"
                            + " CamelIBMWatsonSTTWordConfidence header replaces this option.")
    private boolean wordConfidence = false;

    @UriParam(label = "producer", defaultValue = "false",
              description = "Whether recognize returns labels that identify which speaker spoke which words. The service"
                            + " then also returns the timestamps of the words, whatever the timestamps option is. The"
                            + " CamelIBMWatsonSTTSpeakerLabels header replaces this option.")
    private boolean speakerLabels = false;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public WatsonSpeechToTextOperations getOperation() {
        return operation;
    }

    public void setOperation(WatsonSpeechToTextOperations operation) {
        this.operation = operation;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public boolean isTimestamps() {
        return timestamps;
    }

    public void setTimestamps(boolean timestamps) {
        this.timestamps = timestamps;
    }

    public boolean isWordConfidence() {
        return wordConfidence;
    }

    public void setWordConfidence(boolean wordConfidence) {
        this.wordConfidence = wordConfidence;
    }

    public boolean isSpeakerLabels() {
        return speakerLabels;
    }

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
