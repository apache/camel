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
package org.apache.camel.component.huggingface;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

@UriEndpoint(firstVersion = "4.19.0", scheme = "huggingface", title = "Hugging Face",
             syntax = "huggingface:task", producerOnly = true, category = { Category.AI },
             headersClass = HuggingFaceConstants.class)
@Metadata(label = "ai")
@BodyTypes(producerIn = @BodyType(value = Carrier.TEXT, dependsOn = "predictorBean",
                                  byOption = {
                                          @BodyType.When(option = "task", value = "QUESTION_ANSWERING",
                                                         carriers = Carrier.POJO,
                                                         javaType = "ai.djl.modality.nlp.qa.QAInput"),
                                          @BodyType.When(option = "task", value = "ZERO_SHOT_CLASSIFICATION",
                                                         carriers = Carrier.LIST),
                                          @BodyType.When(option = "task", value = "SENTENCE_EMBEDDINGS",
                                                         carriers = { Carrier.TEXT, Carrier.LIST }),
                                          @BodyType.When(option = "task", value = "AUTOMATIC_SPEECH_RECOGNITION",
                                                         carriers = Carrier.POJO, javaType = "ai.djl.modality.audio.Audio"),
                                          @BodyType.When(option = "task", value = "CHAT",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE,
                                                                 Carrier.NULL }),
                                          @BodyType.When(option = "task", value = "CUSTOM", carriers = Carrier.ANY) },
                                  description = "Most tasks take the text as a String, and fail on bytes or a stream: the"
                                                + " text to classify, the prompt, or the text to summarize or speak."
                                                + " question-answering takes a DJL QAInput, zero-shot-classification a List"
                                                + " or array of Strings with the text first and then the labels,"
                                                + " sentence-embeddings a String or a List or array of them, and"
                                                + " automatic-speech-recognition a DJL Audio. chat converts the body to the"
                                                + " text of the user message, and a null body sends the history alone. With"
                                                + " predictorBean, the predictor bean reads the body"),
           producerOut = @BodyType(value = Carrier.TEXT, dependsOn = "predictorBean",
                                   byOption = {
                                           @BodyType.When(option = "task", value = "TEXT_CLASSIFICATION",
                                                          carriers = Carrier.POJO,
                                                          javaType = "ai.djl.modality.Classifications"),
                                           @BodyType.When(option = "task", value = "TEXT_GENERATION", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "task", value = "QUESTION_ANSWERING",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "task", value = "SUMMARIZATION", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "task", value = "ZERO_SHOT_CLASSIFICATION",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "task", value = "SENTENCE_EMBEDDINGS",
                                                          carriers = Carrier.LIST, javaType = "float[][]"),
                                           @BodyType.When(option = "task", value = "TEXT_TO_IMAGE", carriers = Carrier.BYTES,
                                                          javaType = "byte[]"),
                                           @BodyType.When(option = "task", value = "AUTOMATIC_SPEECH_RECOGNITION",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "task", value = "TEXT_TO_SPEECH", carriers = Carrier.POJO,
                                                          javaType = "ai.djl.modality.audio.Audio"),
                                           @BodyType.When(option = "task", value = "CHAT", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "task", value = "CUSTOM", carriers = Carrier.UNKNOWN),
                                           @BodyType.When(option = "autoSelect", value = "false", carriers = Carrier.POJO,
                                                          javaType = "ai.djl.modality.Classifications") },
                                   description = "The result as text for text-generation, question-answering (the answer),"
                                                 + " summarization, automatic-speech-recognition (the transcript) and chat"
                                                 + " (the reply). text-classification gives DJL Classifications, as"
                                                 + " zero-shot-classification does with autoSelect=false (else the best"
                                                 + " label as text); sentence-embeddings a float[][] with a vector per"
                                                 + " sentence, text-to-image the PNG image as bytes, and text-to-speech a"
                                                 + " DJL Audio. The CamelHuggingFaceOutput header has the result too, as the"
                                                 + " raw JSON for the classifications and the embeddings"))
public class HuggingFaceEndpoint extends DefaultEndpoint {

    @UriParam
    private HuggingFaceConfiguration configuration;

    public HuggingFaceEndpoint(String uri, Component component, HuggingFaceConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HuggingFaceProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for HuggingFace endpoint");
    }

    public HuggingFaceConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(HuggingFaceConfiguration configuration) {
        this.configuration = configuration;
    }
}
