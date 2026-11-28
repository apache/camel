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

import java.util.Map;

import com.ibm.cloud.sdk.core.service.exception.ServiceResponseException;
import com.ibm.watson.speech_to_text.v1.SpeechToText;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;

/**
 * Checks that the Speech to Text service answers, by listing its models.
 */
public class WatsonSpeechToTextProducerHealthCheck extends AbstractHealthCheck {

    private final WatsonSpeechToTextEndpoint endpoint;

    public WatsonSpeechToTextProducerHealthCheck(WatsonSpeechToTextEndpoint endpoint, String clientId) {
        super("camel", "producer:ibm-watson-speech-to-text-" + clientId);
        this.endpoint = endpoint;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        SpeechToText stt = endpoint.getSttClient();
        if (stt == null) {
            builder.message("The Speech to Text client is not created");
            builder.down();
            return;
        }
        try {
            stt.listModels().execute();
        } catch (ServiceResponseException e) {
            builder.message(e.getMessage());
            builder.error(e);
            builder.detail(SERVICE_STATUS_CODE, e.getStatusCode());
            builder.down();
            return;
        } catch (Exception e) {
            builder.error(e);
            builder.down();
            return;
        }
        builder.up();
    }
}
