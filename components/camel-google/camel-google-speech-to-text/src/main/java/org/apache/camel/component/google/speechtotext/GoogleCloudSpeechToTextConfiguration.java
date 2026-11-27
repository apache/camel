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

    @UriPath(label = "common",
             description = "The operation to perform. The only operation is recognize, which sends the audio in one synchronous"
                           + " call, so it suits short audio (the service accepts up to one minute). The"
                           + " CamelGoogleCloudSpeechToTextOperation header overrides it, and with pojoRequest=true it is ignored.")
    @Metadata(required = true)
    private String operation;

    @UriParam(label = "common",
              description = "The service account key file (JSON) used to create the SpeechClient. It is loaded as a resource with"
                            + " the classpath:, file:, http: or https: prefix, and a value without a prefix is read from the"
                            + " classpath. When not set, the Application Default Credentials are used, for example from the"
                            + " GOOGLE_APPLICATION_CREDENTIALS environment variable. Not used when the client option is set.")
    private String serviceAccountKey;

    @UriParam(defaultValue = "LINEAR16", label = "producer",
              description = "The encoding of the audio in the message body, as a name of the RecognitionConfig.AudioEncoding"
                            + " enum: ENCODING_UNSPECIFIED, LINEAR16, FLAC, MULAW, AMR, AMR_WB, OGG_OPUS,"
                            + " SPEEX_WITH_HEADER_BYTE, MP3 or WEBM_OPUS. Use ENCODING_UNSPECIFIED to let the service read the"
                            + " encoding from the header of a FLAC or WAV file. Google documents MP3 as available only in the"
                            + " v1p1beta1 API, while this component calls the v1 API.")
    private String encoding = "LINEAR16";

    @UriParam(label = "producer",
              description = "The sample rate in Hertz of the audio in the message body, from 8000 to 48000. When not set, no"
                            + " rate is sent, which works for FLAC and WAV files that carry it in their header. The service"
                            + " requires it for the other encodings.")
    private Integer sampleRateHertz;

    @UriParam(defaultValue = "en-US", label = "producer",
              description = "The language of the speech in the audio, as a BCP-47 language tag such as en-US or fr-FR.")
    private String languageCode = "en-US";

    @UriParam(defaultValue = "false",
              description = "Whether the message body is a RecognizeRequest of the Google Cloud SDK, which is sent as it is."
                            + " The request then carries the audio and its RecognitionConfig, so the operation, encoding,"
                            + " sampleRateHertz and languageCode options and the operation header are not used.")
    private boolean pojoRequest;

    @UriParam(label = "advanced",
              description = "The SpeechClient to use for the calls. When not set, a single SpeechClient in the registry is"
                            + " used, otherwise the endpoint creates one with the serviceAccountKey or the Application"
                            + " Default Credentials and closes it when it stops. A client that is set or autowired is not closed by the endpoint.")
    @Metadata(autowired = true)
    private SpeechClient client;

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    @Override
    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public Integer getSampleRateHertz() {
        return sampleRateHertz;
    }

    public void setSampleRateHertz(Integer sampleRateHertz) {
        this.sampleRateHertz = sampleRateHertz;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public SpeechClient getClient() {
        return client;
    }

    public void setClient(SpeechClient client) {
        this.client = client;
    }

    public boolean isPojoRequest() {
        return pojoRequest;
    }

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
