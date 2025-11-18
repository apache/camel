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

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel IBM Watson Text to Speech module
 */
public interface WatsonTextToSpeechConstants {

    @Metadata(description = "The operation to perform", javaType = "String")
    String OPERATION = "CamelIBMWatsonTTSOperation";

    // Synthesis headers
    @Metadata(description = "The text to synthesize into speech", javaType = "String")
    String TEXT = "CamelIBMWatsonTTSText";
    @Metadata(description = "The voice to use for synthesis", javaType = "String")
    String VOICE = "CamelIBMWatsonTTSVoice";
    @Metadata(description = "The audio format (e.g., audio/wav, audio/mp3, audio/ogg)", javaType = "String")
    String ACCEPT = "CamelIBMWatsonTTSAccept";
    @Metadata(description = "The customization ID for a custom voice model", javaType = "String")
    String CUSTOMIZATION_ID = "CamelIBMWatsonTTSCustomizationId";

    // Pronunciation headers
    @Metadata(description = "The word for which to get pronunciation", javaType = "String")
    String WORD = "CamelIBMWatsonTTSWord";
    @Metadata(description = "The pronunciation format (ipa or ibm)", javaType = "String")
    String FORMAT = "CamelIBMWatsonTTSFormat";

    // Custom model headers
    @Metadata(description = "The language for filtering custom models", javaType = "String")
    String LANGUAGE = "CamelIBMWatsonTTSLanguage";
    @Metadata(description = "The custom model ID", javaType = "String")
    String MODEL_ID = "CamelIBMWatsonTTSModelId";

    // Voice info headers
    @Metadata(description = "The name of the voice", javaType = "String")
    String VOICE_NAME = "CamelIBMWatsonTTSVoiceName";
}
