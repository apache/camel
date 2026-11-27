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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.huggingface.HuggingFaceConfiguration;
import org.apache.camel.component.huggingface.HuggingFaceEndpoint;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

public abstract class AbstractTaskPredictor implements TaskPredictor {

    protected static final Logger LOG = LoggerFactory.getLogger(AbstractTaskPredictor.class);
    // reads a whole output as one JSON value, so a text that only starts with a JSON object is not read as one
    private static final ObjectReader OUTPUT_READER
            = JsonMapper.builderWithJackson2Defaults().build().reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final ReentrantLock lock = new ReentrantLock();
    protected HuggingFaceEndpoint endpoint;
    protected HuggingFaceConfiguration config;
    protected ZooModel<Input, Output> model;
    protected Predictor<Input, Output> predictor;
    protected Path tmpDir;

    protected AbstractTaskPredictor(HuggingFaceEndpoint endpoint) {
        this.endpoint = endpoint;
        this.config = endpoint.getConfiguration();
    }

    protected AbstractTaskPredictor() {
    }

    @Override
    public void loadModel() throws Exception {
        tmpDir = createModelDir();
        try {
            Path reqPath = writeModelFiles(tmpDir);
            String modelUrl = "file://" + tmpDir.toAbsolutePath();
            Criteria.Builder<Input, Output> criteriaBuilder = Criteria.builder()
                    .setTypes(Input.class, Output.class)
                    .optModelUrls(modelUrl)
                    .optEngine("Python");
            modelOptions(reqPath).forEach(criteriaBuilder::optOption);
            Criteria<Input, Output> criteria = criteriaBuilder.build();
            model = criteria.loadModel();
        } catch (Exception e) {
            try {
                deleteModelDir();
            } catch (IOException ex) {
                e.addSuppressed(ex);
            }
            throw e;
        }
    }

    private static Path createModelDir() throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return Files.createTempDirectory("hf_model",
                    PosixFilePermissions.asFileAttribute(
                            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                                    PosixFilePermission.OWNER_EXECUTE)));
        }
        return Files.createTempDirectory("hf_model");
    }

    /**
     * Writes the Python handler and its requirements into the model directory, and returns the path of the requirements
     * file. The handler holds no secret: the token is given to the Python process as an environment variable, see
     * {@link #modelOptions(Path)}.
     */
    Path writeModelFiles(Path dir) throws IOException {
        String pythonScript = getPythonScript();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Generated Python script for task {}:\n{}", config.getTask(), pythonScript);
        }
        Files.writeString(dir.resolve("handler.py"), pythonScript);
        Path reqPath = dir.resolve("requirements.txt");
        Files.writeString(reqPath, getRequirements());
        return reqPath;
    }

    /**
     * The options of the DJL Python engine. The Hugging Face token (the {@code authToken} option, or the token resolved
     * from the OAuth profile) is given to the Python process as the {@code HF_TOKEN} environment variable, which
     * {@code transformers} and {@code huggingface_hub} read when no explicit token is passed, so every task can load
     * gated or private models.
     */
    Map<String, String> modelOptions(Path reqPath) {
        Map<String, String> options = new LinkedHashMap<>();
        options.put("handler", "handle");
        options.put("entryPoint", "handler.py");
        options.put("requirementsFile", reqPath.toString());
        if (config.getModelLoadingTimeout() > 0) {
            options.put("model_loading_timeout", String.valueOf(config.getModelLoadingTimeout()));
        }
        if (config.getPredictTimeout() > 0) {
            options.put("predict_timeout", String.valueOf(config.getPredictTimeout()));
        }
        String authToken = config.getAuthToken();
        if (authToken != null && !authToken.isBlank()) {
            authToken = authToken.strip();
            // the Python engine reads the env option as a comma separated list of name=value
            if (authToken.indexOf(',') >= 0 || authToken.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException(
                        "The authToken must not contain a comma or a control character");
            }
            options.put("env", "HF_TOKEN=" + authToken);
        }
        return options;
    }

    @Override
    public void setEndpoint(HuggingFaceEndpoint endpoint) {
        this.endpoint = endpoint;
        this.config = endpoint.getConfiguration();
    }

    protected abstract String getPythonScript();

    protected String loadPythonScript(String resourcePath, Object... args) {
        InputStream is = null;
        try {
            String fullPath = "classpath:org/apache/camel/component/huggingface/tasks/" + resourcePath;
            is = ResourceHelper.resolveResourceAsInputStream(endpoint.getCamelContext(), fullPath);
            String script = IOHelper.loadText(is);
            // Locale.ROOT, so a float is written with a dot in every locale
            return String.format(Locale.ROOT, script, args);
        } catch (IOException e) {
            throw new RuntimeCamelException("Failed to load python script: " + resourcePath, e);
        } finally {
            IOHelper.close(is);
        }
    }

    /**
     * Returns the value as a Python string literal, to put into the generated handler: the backslash and the single
     * quote are escaped, and every character that is not printable ASCII is written as an escape sequence, so the value
     * cannot end the literal or the line. A {@code null} value is {@code None}.
     */
    protected static String pythonString(String value) {
        if (value == null) {
            return "None";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('\'');
        value.codePoints().forEach(cp -> {
            if (cp == '\\') {
                sb.append("\\\\");
            } else if (cp == '\'') {
                sb.append("\\'");
            } else if (cp >= 0x20 && cp < 0x7f) {
                sb.append((char) cp);
            } else if (cp <= 0xff) {
                sb.append(String.format(Locale.ROOT, "\\x%02x", cp));
            } else if (cp <= 0xffff) {
                sb.append(String.format(Locale.ROOT, "\\u%04x", cp));
            } else {
                sb.append(String.format(Locale.ROOT, "\\U%08x", cp));
            }
        });
        sb.append('\'');
        return sb.toString();
    }

    protected String getRequirements() {
        return """
                transformers>=4.30.0
                torch>=2.0.0
                accelerate
                """;
    }

    @Override
    public void predict(Exchange exchange) throws Exception {
        Input input = prepareInput(exchange);
        Output output = infer(input);
        processOutput(exchange, output);
    }

    /**
     * Runs the model on the input. With pooling, the calls share one predictor and run one at a time.
     */
    protected Output infer(Input input) throws Exception {
        if (config.isPooling()) {
            lock.lock();
            try {
                if (predictor == null) {
                    predictor = model.newPredictor();
                }
                return predictor.predict(input);
            } finally {
                lock.unlock();
            }
        } else {
            try (Predictor<Input, Output> djlPredictor = model.newPredictor()) {
                return djlPredictor.predict(input);
            }
        }
    }

    /**
     * Fails when the output of the Python handler is its error payload: a JSON object with the single field
     * {@code error}, or when there is no output. Any other output is a result, also a text or a JSON that has the word
     * error in it.
     */
    protected static void failOnPythonError(String output) {
        if (output == null) {
            throw new RuntimeCamelException("Python inference returned no output");
        }
        String text = output.strip();
        // only an output that starts with an error field can be the error payload, so other outputs are not parsed
        if (text.startsWith("{") && text.substring(1).stripLeading().startsWith("\"error\"")) {
            JsonNode node;
            try {
                node = OUTPUT_READER.readTree(text);
            } catch (JacksonException e) {
                // not a JSON object, so a text result
                return;
            }
            failOnPythonError(node);
        }
    }

    /**
     * Fails when the JSON output of the Python handler is its error payload: an object with the single field
     * {@code error}.
     */
    protected static void failOnPythonError(JsonNode output) {
        if (output != null && output.isObject() && output.size() == 1 && output.has("error")) {
            throw new RuntimeCamelException("Python inference failed: " + output.get("error").asString());
        }
    }

    protected abstract Input prepareInput(Exchange exchange) throws Exception;

    protected abstract void processOutput(Exchange exchange, Output output) throws Exception;

    @Override
    public void close() throws Exception {
        try {
            if (predictor != null) {
                predictor.close();
            }
            if (model != null) {
                model.close();
            }
        } finally {
            deleteModelDir();
        }
    }

    private void deleteModelDir() throws IOException {
        Path modelDir = tmpDir;
        tmpDir = null;
        if (modelDir != null && Files.exists(modelDir)) {
            Files.walkFileTree(modelDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
