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
package org.apache.camel.component.huggingface.tasks;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.component.huggingface.HuggingFaceConfiguration;
import org.apache.camel.component.huggingface.HuggingFaceEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The option values put into the generated Python handler must be proper Python string literals, so a quote, a
 * backslash or a newline in a value cannot end the literal or the line, and the temporary model directory must be
 * removed.
 */
class HandlerValuesTest {

    @TempDir
    Path dir;

    private DefaultCamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private HuggingFaceEndpoint endpoint(HuggingFaceConfiguration config) {
        HuggingFaceEndpoint endpoint = new HuggingFaceEndpoint(null, null, config);
        endpoint.setCamelContext(context);
        return endpoint;
    }

    @Test
    void pythonStringEscapesBackslashQuoteAndControlCharacters() {
        assertEquals("'gpt2'", AbstractTaskPredictor.pythonString("gpt2"));
        assertEquals("'a\\\\b\\'c\"d\\x0ae\\x0d\\x09'", AbstractTaskPredictor.pythonString("a\\b'c\"d\ne\r\t"));
        assertEquals("'caf\\xe9 \\u20ac \\U0001f600'", AbstractTaskPredictor.pythonString("café € 😀"));
        assertEquals("None", AbstractTaskPredictor.pythonString(null));
    }

    @Test
    void valueWithBackslashQuoteAndNewlineStaysInsideTheLiteral() {
        HuggingFaceConfiguration plain = new HuggingFaceConfiguration();
        plain.setModelId("gpt2");
        String plainScript = new TextGenerationPredictor(endpoint(plain)).getPythonScript();

        HuggingFaceConfiguration config = new HuggingFaceConfiguration();
        config.setModelId("org/m\\', revision='x\nimport os");
        config.setRevision("v1\\");
        config.setDevice("cpu'");
        String script = new TextGenerationPredictor(endpoint(config)).getPythonScript();

        assertTrue(script.contains("model='org/m\\\\\\', revision=\\'x\\x0aimport os',"), script);
        assertTrue(script.contains("revision='v1\\\\',"), script);
        assertTrue(script.contains("device_map='cpu\\''"), script);
        assertEquals(plainScript.lines().count(), script.lines().count(), "a value must not add a line to the handler");
    }

    @Test
    void everyTaskPutsTheValuesAsLiterals() {
        HuggingFaceConfiguration config = new HuggingFaceConfiguration();
        config.setModelId("a'b");
        config.setRevision("r'1");
        config.setDevice("d'1");
        HuggingFaceEndpoint endpoint = endpoint(config);
        AbstractTaskPredictor[] predictors = {
                new AutomaticSpeechRecognitionPredictor(endpoint), new ChatPredictor(endpoint),
                new QuestionAnsweringPredictor(endpoint), new SentenceEmbeddingsPredictor(endpoint),
                new SummarizationPredictor(endpoint), new TextClassificationPredictor(endpoint),
                new TextGenerationPredictor(endpoint), new TextToImagePredictor(endpoint),
                new TextToSpeechPredictor(endpoint), new ZeroShotClassificationPredictor(endpoint) };
        for (AbstractTaskPredictor predictor : predictors) {
            String script = predictor.getPythonScript();
            String name = predictor.getClass().getSimpleName();
            assertTrue(script.contains("'a\\'b'"), name + " must put the model ID as a literal");
            assertTrue(script.contains("'r\\'1'"), name + " must put the revision as a literal");
            assertTrue(script.contains("'d\\'1'"), name + " must put the device as a literal");
            assertFalse(script.contains("a'b"), name + " must not put the model ID unescaped");
        }
    }

    @Test
    @ResourceLock(Resources.LOCALE)
    void temperatureIsWrittenWithADotInEveryLocale() {
        HuggingFaceConfiguration config = new HuggingFaceConfiguration();
        config.setModelId("gpt2");
        config.setTemperature(0.5f);
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            String script = new TextGenerationPredictor(endpoint(config)).getPythonScript();
            assertTrue(script.contains("'temperature': 0.500000"), script);
        } finally {
            Locale.setDefault(old);
        }
    }

    @Test
    void closeRemovesTheModelDirectory() throws Exception {
        HuggingFaceConfiguration config = new HuggingFaceConfiguration();
        config.setModelId("gpt2");
        TextGenerationPredictor predictor = new TextGenerationPredictor(endpoint(config));
        Path modelDir = Files.createDirectory(dir.resolve("hf_model"));
        predictor.tmpDir = modelDir;
        predictor.writeModelFiles(modelDir);
        assertTrue(Files.exists(modelDir.resolve("handler.py")));

        predictor.close();

        assertFalse(Files.exists(modelDir), "the model directory must be removed on close");
        assertNull(predictor.tmpDir);
    }

    @Test
    void modelDirectoryIsRemovedWhenTheModelFailsToLoad() {
        HuggingFaceConfiguration config = new HuggingFaceConfiguration();
        config.setModelId("gpt2");
        AtomicReference<Path> modelDir = new AtomicReference<>();
        AtomicReference<String> permissions = new AtomicReference<>();
        TextGenerationPredictor predictor = new TextGenerationPredictor(endpoint(config)) {
            @Override
            protected String getRequirements() {
                // called once handler.py is written, before the Python engine is started
                modelDir.set(tmpDir);
                if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                    try {
                        permissions.set(PosixFilePermissions.toString(Files.getPosixFilePermissions(tmpDir)));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
                throw new IllegalStateException("cannot load");
            }
        };

        assertThrows(IllegalStateException.class, predictor::loadModel);

        assertNotNull(modelDir.get());
        assertFalse(Files.exists(modelDir.get()), "the model directory must be removed when loading fails");
        if (permissions.get() != null) {
            assertEquals("rwx------", permissions.get(), "only the owner may read the model directory");
        }
    }
}
