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
package org.apache.camel.component.djl.model;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.modality.audio.Audio;
import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.timeseries.TimeSeriesData;
import ai.djl.translate.Translator;
import org.apache.camel.Exchange;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.djl.DJLComponent;
import org.apache.camel.component.djl.DJLConstants;
import org.apache.camel.component.djl.DJLEndpoint;
import org.apache.camel.component.djl.model.audio.ZooAudioPredictor;
import org.apache.camel.component.djl.model.cv.AbstractCvZooPredictor;
import org.apache.camel.component.djl.model.nlp.AbstractNlpZooPredictor;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The body handling of the predictors and the lookup of a custom model, with a mocked DJL model and predictor so no
 * model is loaded or downloaded.
 */
class PredictorBodyTest extends CamelTestSupport {

    private static final String IMAGE_FILE = "src/test/resources/data/mnist/0/10.png";

    private final Model model = mock(Model.class);
    private final ZooModel<Object, Object> zooModel = mock(ZooModel.class);
    private final Predictor<Object, Object> predictor = mock(Predictor.class);
    private final Translator<Object, Object> translator = mock(Translator.class);

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("MyModel", model);
        registry.bind("MyTranslator", translator);
    }

    @BeforeEach
    void setupPredictor() throws Exception {
        when(model.newPredictor(any(Translator.class))).thenReturn(predictor);
        when(zooModel.newPredictor()).thenReturn(predictor);
        when(predictor.predict(any())).thenAnswer(invocation -> "predicted");
    }

    @Test
    void customCvPredictorRejectsBodyWithoutConverter() throws Exception {
        try (Producer producer = startProducer("djl:cv/image_classification?model=MyModel&translator=MyTranslator")) {
            for (Object body : new Object[] {
                    "not an image", Map.of("a", 1), new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), null }) {
                assertNotSupported(producer, body, "ai.djl.modality.cv.Image, byte[], InputStream or File");
            }
            verify(predictor, never()).predict(any());
        }
    }

    @Test
    void customCvPredictorConvertsImageBytes() throws Exception {
        try (Producer producer = startProducer("djl:cv/image_classification?model=MyModel&translator=MyTranslator")) {
            Exchange exchange = exchange(Files.readAllBytes(Paths.get(IMAGE_FILE)));
            producer.process(exchange);

            assertEquals("predicted", exchange.getMessage().getBody());
            assertInstanceOf(Image.class, exchange.getMessage().getHeader(DJLConstants.INPUT));
        }
    }

    @Test
    void zooCvPredictorRejectsBodyWithoutConverter() throws Exception {
        AbstractCvZooPredictor<Object> cv = new AbstractCvZooPredictor<>(endpoint("cv/image_classification")) {
            {
                model = mockZooModel();
            }
        };
        for (Object body : new Object[] {
                "not an image", Map.of("a", 1), new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), null }) {
            assertNotSupported(cv, body, "ai.djl.modality.cv.Image, byte[], InputStream or File");
        }
        verify(predictor, never()).predict(any());
    }

    @Test
    void customAudioPredictorRejectsBodyWithoutConverter() throws Exception {
        try (Producer producer = startProducer("djl:audio?model=MyModel&translator=MyTranslator")) {
            for (Object body : new Object[] { "not an audio clip", Map.of("a", 1), null }) {
                assertNotSupported(producer, body, "ai.djl.modality.audio.Audio, byte[], InputStream or File");
            }
            verify(predictor, never()).predict(any());

            Audio audio = new Audio(new float[] { 0.1f, 0.2f });
            Exchange exchange = exchange(audio);
            producer.process(exchange);
            assertEquals("predicted", exchange.getMessage().getBody());
            verify(predictor).predict(audio);
        }
    }

    @Test
    void zooAudioPredictorRejectsBodyWithoutConverter() throws Exception {
        DJLEndpoint endpoint = endpoint("audio");
        endpoint.setArtifactId("ai.djl.test:audio:0.0.1");
        ZooAudioPredictor audio;
        try (MockedStatic<ModelZoo> zoo = mockStatic(ModelZoo.class)) {
            zoo.when(() -> ModelZoo.loadModel(any(Criteria.class))).thenReturn(zooModel);
            audio = new ZooAudioPredictor(endpoint);
        }
        for (Object body : new Object[] { "not an audio clip", Map.of("a", 1), null }) {
            assertNotSupported(audio, body, "ai.djl.modality.audio.Audio, byte[], InputStream or File");
        }
        verify(predictor, never()).predict(any());
    }

    @Test
    void customNlpPredictorTakesTextAsStringBytesOrStream() throws Exception {
        try (Producer producer = startProducer("djl:nlp/sentiment_analysis?model=MyModel&translator=MyTranslator")) {
            byte[] text = "I like Camel".getBytes(StandardCharsets.UTF_8);
            for (Object body : new Object[] { "I like Camel", text, new ByteArrayInputStream(text) }) {
                Exchange exchange = exchange(body);
                producer.process(exchange);
                assertEquals("predicted", exchange.getMessage().getBody());
                assertEquals("I like Camel", exchange.getMessage().getHeader(DJLConstants.INPUT));
            }

            assertNotSupported(producer, null, "String, byte[] or InputStream");
        }
    }

    @Test
    void zooNlpPredictorTakesTextAsStringBytesOrStream() throws Exception {
        AbstractNlpZooPredictor<Object> nlp = new AbstractNlpZooPredictor<>(endpoint("nlp/sentiment_analysis")) {
            {
                model = mockZooModel();
            }
        };
        byte[] text = "I like Camel".getBytes(StandardCharsets.UTF_8);
        for (Object body : new Object[] { "I like Camel", text, new ByteArrayInputStream(text) }) {
            Exchange exchange = exchange(body);
            nlp.process(exchange);
            assertEquals("predicted", exchange.getMessage().getBody());
            assertEquals("I like Camel", exchange.getMessage().getHeader(DJLConstants.INPUT));
        }

        assertNotSupported(nlp, null, "String, byte[] or InputStream");
    }

    @Test
    void customPredictorNamesTheMissingBean() throws Exception {
        Map<String, Object> bodies = new LinkedHashMap<>();
        bodies.put("cv/image_classification", Files.readAllBytes(Paths.get(IMAGE_FILE)));
        bodies.put("cv/image_generation", new int[] { 1, 2 });
        bodies.put("nlp/question_answer", new String[] { "Who?", "Camel." });
        bodies.put("nlp/text_generation", "Camel");
        bodies.put("audio", new Audio(new float[] { 0.1f }));
        bodies.put("tabular/linear_regression", "1,2,3");
        bodies.put("timeseries/forecasting", new TimeSeriesData(1));
        for (Map.Entry<String, Object> entry : bodies.entrySet()) {
            String uri = "djl:" + entry.getKey() + "?model=NoSuchModel&translator=MyTranslator";
            try (Producer producer = startProducer(uri)) {
                NoSuchBeanException e = assertThrows(NoSuchBeanException.class,
                        () -> producer.process(exchange(entry.getValue())), uri);
                assertEquals("NoSuchModel", e.getName());
            }

            uri = "djl:" + entry.getKey() + "?model=MyModel&translator=NoSuchTranslator";
            try (Producer producer = startProducer(uri)) {
                NoSuchBeanException e = assertThrows(NoSuchBeanException.class,
                        () -> producer.process(exchange(entry.getValue())), uri);
                assertEquals("NoSuchTranslator", e.getName());
            }
        }
        verify(predictor, never()).predict(any());
    }

    @Test
    void customPredictorLooksUpTheModelOnce() throws Exception {
        try (Producer producer = startProducer("djl:nlp/text_generation?model=MyModel&translator=MyTranslator")) {
            producer.process(exchange("first"));

            context.getRegistry().unbind("MyModel");
            context.getRegistry().unbind("MyTranslator");
            Exchange exchange = exchange("second");
            producer.process(exchange);

            assertEquals("predicted", exchange.getMessage().getBody());
            verify(model, times(2)).newPredictor(translator);
        }
    }

    @Test
    void customPredictorFailsToStartWithoutModelOption() {
        Exception e = assertThrows(Exception.class,
                () -> startProducer("djl:cv/image_classification?translator=MyTranslator"));
        IllegalArgumentException cause = ObjectHelper.getException(IllegalArgumentException.class, e);
        assertNotNull(cause, e.toString());
        assertTrue(cause.getMessage().contains("model"), cause.getMessage());
    }

    @SuppressWarnings("unchecked")
    private <I, O> ZooModel<I, O> mockZooModel() {
        return (ZooModel<I, O>) zooModel;
    }

    private Producer startProducer(String uri) throws Exception {
        Producer producer = context.getEndpoint(uri).createProducer();
        producer.start();
        return producer;
    }

    private DJLEndpoint endpoint(String application) {
        DJLEndpoint endpoint
                = new DJLEndpoint("djl:" + application, context.getComponent("djl", DJLComponent.class), application);
        endpoint.setCamelContext(context);
        return endpoint;
    }

    private Exchange exchange(Object body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        return exchange;
    }

    private void assertNotSupported(Producer producer, Object body, String types) {
        RuntimeCamelException e = assertThrows(RuntimeCamelException.class, () -> producer.process(exchange(body)),
                "body: " + body);
        assertEquals("Data type is not supported. Body should be " + types, e.getMessage());
    }

    private void assertNotSupported(AbstractPredictor predictor, Object body, String types) {
        RuntimeCamelException e = assertThrows(RuntimeCamelException.class, () -> predictor.process(exchange(body)),
                "body: " + body);
        assertEquals("Data type is not supported. Body should be " + types, e.getMessage());
    }
}
