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
package org.apache.camel.component.openai;

import com.openai.models.audio.translations.TranslationCreateParams;
import com.openai.models.audio.translations.TranslationCreateResponse;
import com.openai.models.audio.translations.TranslationVerbose;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * OpenAI producer for audio translation (transcribe-and-translate to English).
 */
public class OpenAIAudioTranslationProducer extends DefaultProducer {

    public OpenAIAudioTranslationProducer(OpenAIEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public OpenAIEndpoint getEndpoint() {
        return (OpenAIEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        OpenAIConfiguration config = getEndpoint().getConfiguration();
        Message in = exchange.getIn();

        String model = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.AUDIO_MODEL, config.getAudioModel(),
                String.class);
        if (model == null) {
            throw new IllegalArgumentException(
                    "Audio model must be specified via audioModel parameter or CamelOpenAIAudioModel header");
        }

        String responseFormat = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.AUDIO_RESPONSE_FORMAT,
                config.getAudioResponseFormat(), String.class);
        Double temperature = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.AUDIO_TEMPERATURE,
                config.getAudioTemperature(), Double.class);
        String prompt = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.AUDIO_PROMPT, config.getAudioPrompt(),
                String.class);

        TranslationCreateParams.Builder paramsBuilder = TranslationCreateParams.builder()
                .model(model);

        OpenAIAudioSupport.applyFileInput(in, paramsBuilder::file, paramsBuilder::file);

        if (ObjectHelper.isNotEmpty(prompt)) {
            paramsBuilder.prompt(prompt);
        }
        if (ObjectHelper.isNotEmpty(responseFormat)) {
            paramsBuilder.responseFormat(TranslationCreateParams.ResponseFormat.of(responseFormat));
        }
        if (temperature != null) {
            paramsBuilder.temperature(temperature);
        }

        TranslationCreateParams params = paramsBuilder.build();
        TranslationCreateResponse response = getEndpoint().getClient()
                .audio().translations().create(params);

        Message out = exchange.getMessage();

        if (response.isVerbose()) {
            TranslationVerbose verbose = response.asVerbose();
            out.setBody(verbose.text());
            out.setHeader(OpenAIConstants.AUDIO_DURATION, verbose.duration());
            out.setHeader(OpenAIConstants.AUDIO_DETECTED_LANGUAGE, verbose.language());
        } else if (response.isTranslation()) {
            out.setBody(response.asTranslation().text());
        } else {
            out.setBody(response.toString());
        }

        if (config.isStoreFullResponse()) {
            exchange.setProperty(OpenAIConstants.RESPONSE, response);
        }
    }
}
