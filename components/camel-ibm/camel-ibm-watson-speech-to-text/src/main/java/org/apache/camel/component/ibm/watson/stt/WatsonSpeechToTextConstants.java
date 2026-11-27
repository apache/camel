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

    @Metadata(description = "The operation that the producer performs. It replaces the operation option.", javaType = "String")
    String OPERATION = "CamelIBMWatsonSTTOperation";

    // Recognition headers
    @Metadata(description = "The audio file that recognize transcribes. If set, the producer sends this file instead of the"
                            + " message body.",
              javaType = "java.io.File")
    String AUDIO_FILE = "CamelIBMWatsonSTTAudioFile";
    @Metadata(description = "The speech model that recognize uses. It replaces the model option. The producer sets it on"
                            + " the result to the model it used.",
              javaType = "String")
    String MODEL = "CamelIBMWatsonSTTModel";
    @Metadata(description = "The format (MIME type) of the audio for recognize. It replaces the contentType option. The"
                            + " producer sets it on the result to the format it sent.",
              javaType = "String")
    String CONTENT_TYPE = "CamelIBMWatsonSTTContentType";
    @Metadata(description = "Whether recognize returns the start and end time of each word. It replaces the timestamps"
                            + " option.",
              javaType = "Boolean")
    String TIMESTAMPS = "CamelIBMWatsonSTTTimestamps";
    @Metadata(description = "Whether recognize returns a confidence score for each word. It replaces the wordConfidence"
                            + " option.",
              javaType = "Boolean")
    String WORD_CONFIDENCE = "CamelIBMWatsonSTTWordConfidence";
    @Metadata(description = "Whether recognize returns labels that identify the speaker of each word. It replaces the"
                            + " speakerLabels option.",
              javaType = "Boolean")
    String SPEAKER_LABELS = "CamelIBMWatsonSTTSpeakerLabels";

    // Model headers
    @Metadata(description = "The name of the speech model for getModel, or the customization ID of the custom language"
                            + " model for getCustomModel. If not set, the producer reads it from the message body.",
              javaType = "String")
    String MODEL_NAME = "CamelIBMWatsonSTTModelName";
    @Metadata(description = "The language for listCustomModels, for example en-US, to list only the custom language models"
                            + " of that language. If not set, all custom language models of the service instance are listed.",
              javaType = "String")
    String LANGUAGE = "CamelIBMWatsonSTTLanguage";

    // Output headers
    @Metadata(description = "The transcript that recognize sets: the text of the first alternative of each result, joined"
                            + " in order.",
              javaType = "String")
    String TRANSCRIPT = "CamelIBMWatsonSTTTranscript";
}
