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

import java.io.IOException;
import java.nio.file.Paths;

import ai.djl.modality.Classifications;
import ai.djl.modality.audio.Audio;
import ai.djl.modality.audio.AudioFactory;
import ai.djl.modality.nlp.qa.QAInput;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tags({ @Tag("slow") })
public class HuggingFaceIT extends CamelTestSupport {

    @Test
    public void testTextClassification() throws Exception {
        template.sendBody("direct:start", "I love this movie!");
        Classifications output
                = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getMessage().getBody(Classifications.class);
        assertNotNull(output);
        assertEquals(2, output.items().size());
        assertEquals("positive", output.best().getClassName().toLowerCase());
        assertTrue(output.best().getProbability() > 0.5);
    }

    @Test
    public void testTextGeneration() {
        String text = "Once upon a time,";
        template.sendBody("direct:start-gen", text);
        String output = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getMessage().getBody(String.class);
        assertTrue(output.startsWith(text));
        assertTrue(output.length() > text.length());
    }

    @Test
    public void testQuestionAnswering() {
        QAInput qaInput = new QAInput("What is the capital of France?", "The capital of France is Paris.");
        template.sendBody("direct:start-qa", qaInput);
        String output = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getMessage().getBody(String.class);
        assertNotNull(output);
        assertEquals("Paris", output);
    }

    @Test
    public void testSummarization() {
        String inputText = "Apache Camel is an open source framework for message-oriented middleware." +
                           " It uses a rule-based routing and mediation engine to implement Enterprise Integration Patterns (EIPs)."
                           +
                           " The EIPs are implemented using Java objects. Camel has a application programming interface " +
                           "(or declarative Java domain-specific language) for configuring the routing and mediation rules." +
                           " The domain-specific language means that Apache Camel can support type-safe smart completion of " +
                           "routing rules in an integrated development environment using regular Java code without large amounts"
                           +
                           " of XML configuration files, though XML configuration inside Spring Framework is also supported." +
                           " Camel is often used with Apache ServiceMix, Apache ActiveMQ and Apache CXF in service-oriented architecture projects.";
        template.sendBody("direct:start-sum", inputText);
        String output = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getMessage().getBody(String.class);
        assertNotNull(output);
        assertTrue(output.length() < inputText.length());
    }

    @Test
    public void testZeroShotClassification() {
        String[] input = new String[] { "I love this new gadget!", "positive", "negative", "neutral" };
        template.sendBody("direct:start-zero", input);
        String output = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getMessage().getBody(String.class);
        assertEquals("positive", output);
    }

    @Test
    public void testSentenceEmbeddings() {
        template.sendBody("direct:start-feat", "This is a test sentence.");
        float[][] output = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getMessage().getBody(float[][].class);
        assertNotNull(output, "Embeddings should not be null");
        assertEquals(1, output.length, "Expected batch size 1");
        assertTrue(output[0].length > 0, "Embedding dimension too small");
    }

    @Test
    public void testTextToImage() {
        template.sendBody("direct:start-tti", "A cute cat");
        byte[] output = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getMessage().getBody(byte[].class);
        assertNotNull(output);
        assertTrue(output.length > 1000);
    }

    @Test
    public void testAutomaticSpeechRecognition() throws IOException {
        Audio audio = AudioFactory.newInstance().fromFile(Paths.get("generated_audio.wav"));
        template.sendBody("direct:start-asr", audio);
        String output = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getMessage().getBody(String.class);
        assertTrue(output.contains("Hello world."));
    }

    @Test
    public void testTextToSpeech() {
        template.sendBody("direct:start-tts", "Hello world. This is a test audio.");
        Audio audio = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getMessage().getBody(Audio.class);
        assertNotNull(audio);
        assertTrue(audio.getData().length > 1000);
        assertEquals(16000, audio.getSampleRate());
    }

    @Test
    public void testChat() {
        template.sendBody("direct:start-chat", "Hello, what's your name? My name is John.");
        String output = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getMessage().getBody(String.class);
        assertTrue(output.contains("Alan"));
        // Test history
        template.sendBody("direct:start-chat", "What's my name?");
        output = getMockEndpoint("mock:result").getReceivedExchanges().get(1).getMessage().getBody(String.class);
        assertTrue(output.contains("John"));
    }

    @Test
    public void testCustomPredictorTranslation() {
        String inputText = "Hello, how are you?";
        template.sendBody("direct:start-custom", inputText);
        String output = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getMessage().getBody(String.class);
        assertEquals("Bonjour, comment allez-vous ?", output);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("myCustomPredictor", new TranslationPredictor());
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("huggingface:text-classification?modelId=cardiffnlp/twitter-roberta-base-sentiment-latest&topK=2&device=cpu&modelLoadingTimeout=360&predictTimeout=360")
                        .to("mock:result");

                from("direct:start-gen")
                        .to("huggingface:text-generation?modelId=gpt2&device=cpu&maxTokens=50&temperature=0.7&modelLoadingTimeout=360&predictTimeout=360")
                        .to("mock:result");

                from("direct:start-qa")
                        .to("huggingface:question-answering?modelId=distilbert-base-cased-distilled-squad&device=cpu&modelLoadingTimeout=360&predictTimeout=360")
                        .to("mock:result");

                from("direct:start-sum")
                        .to("huggingface:summarization?modelId=facebook/bart-large-cnn&device=cpu&maxTokens=50&minLength=20&modelLoadingTimeout=360&predictTimeout=360")
                        .to("mock:result");

                from("direct:start-zero")
                        .to("huggingface:zero-shot-classification?modelId=facebook/bart-large-mnli&device=cpu&multiLabel=false&modelLoadingTimeout=360&predictTimeout=360")
                        .to("mock:result");

                from("direct:start-feat")
                        .to("huggingface:sentence-embeddings?modelId=sentence-transformers/all-MiniLM-L6-v2&device=cpu&modelLoadingTimeout=360&predictTimeout=360")
                        .to("mock:result");

                from("direct:start-tti")
                        .to("huggingface:text-to-image?modelId=CompVis/stable-diffusion-v1-4&device=cpu&modelLoadingTimeout=650&predictTimeout=10000")
                        .to("mock:result");

                from("direct:start-asr")
                        .to("huggingface:automatic-speech-recognition?modelId=openai/whisper-medium&device=cpu&modelLoadingTimeout=360&predictTimeout=360")
                        .to("mock:result");

                from("direct:start-tts")
                        .to("huggingface:text-to-speech?modelId=facebook/mms-tts-eng&device=cpu&modelLoadingTimeout=360&predictTimeout=360")
                        .to("mock:result");

                from("direct:start-chat")
                        .to("huggingface:chat?modelId=Qwen/Qwen2.5-3B-Instruct&systemPrompt=You are a helpful assistant named Alan&device=cpu&maxTokens=50&temperature=0.7&modelLoadingTimeout=360&predictTimeout=700")
                        .to("mock:result");

                from("direct:start-custom")
                        .to("huggingface:translation?modelId=Helsinki-NLP/opus-mt-en-fr&device=cpu&predictorBean=myCustomPredictor&modelLoadingTimeout=360&predictTimeout=360")
                        .to("mock:result");

            }
        };
    }
}
