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

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel IBM Watson Speech to Text module
 */
public interface WatsonSpeechToTextConstants {

    @Metadata(description = "The operation to perform", javaType = "String")
    String OPERATION = "CamelIBMWatsonSTTOperation";

    // Recognition headers
    @Metadata(description = "The audio file to transcribe", javaType = "java.io.File")
    String AUDIO_FILE = "CamelIBMWatsonSTTAudioFile";

    @Metadata(description = "The language model to use for recognition", javaType = "String")
    String MODEL = "CamelIBMWatsonSTTModel";

    @Metadata(description = "The audio format (e.g., audio/wav, audio/mp3, audio/flac)", javaType = "String")
    String CONTENT_TYPE = "CamelIBMWatsonSTTContentType";

    @Metadata(description = "Whether to include timestamps in the transcription", javaType = "Boolean")
    String TIMESTAMPS = "CamelIBMWatsonSTTTimestamps";

    @Metadata(description = "Whether to include word confidence scores", javaType = "Boolean")
    String WORD_CONFIDENCE = "CamelIBMWatsonSTTWordConfidence";

    @Metadata(description = "Whether to identify different speakers", javaType = "Boolean")
    String SPEAKER_LABELS = "CamelIBMWatsonSTTSpeakerLabels";

    // Model headers
    @Metadata(description = "The name of the model to retrieve", javaType = "String")
    String MODEL_NAME = "CamelIBMWatsonSTTModelName";

    @Metadata(description = "The language for filtering models", javaType = "String")
    String LANGUAGE = "CamelIBMWatsonSTTLanguage";

    // Output headers
    @Metadata(description = "The transcription result text", javaType = "String")
    String TRANSCRIPT = "CamelIBMWatsonSTTTranscript";
}
