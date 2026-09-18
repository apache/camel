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

public abstract class AbstractTaskPredictor implements TaskPredictor {

    protected static final Logger LOG = LoggerFactory.getLogger(AbstractTaskPredictor.class);
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
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            tmpDir = Files.createTempDirectory("hf_model",
                    PosixFilePermissions.asFileAttribute(
                            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                                    PosixFilePermission.OWNER_EXECUTE)));
        } else {
            tmpDir = Files.createTempDirectory("hf_model");
        }
        Path handlerPath = tmpDir.resolve("handler.py");
        String pythonScript = getPythonScript();
        // logged before the token is prepended: withAuthToken writes the configured token into the
        // script, and this now runs for every task rather than only chat
        if (LOG.isDebugEnabled()) {
            LOG.debug("Generated Python script for task {}:\n{}", config.getTask(), pythonScript);
        }
        Files.writeString(handlerPath, withAuthToken(pythonScript));
        Path reqPath = tmpDir.resolve("requirements.txt");
        Files.writeString(reqPath, getRequirements());
        String modelUrl = "file://" + tmpDir.toAbsolutePath();
        Criteria.Builder<Input, Output> criteriaBuilder = Criteria.builder()
                .setTypes(Input.class, Output.class)
                .optModelUrls(modelUrl)
                .optOption("handler", "handle")
                .optEngine("Python")
                .optOption("entryPoint", "handler.py")
                .optOption("requirementsFile", reqPath.toString());

        if (config.getModelLoadingTimeout() > 0) {
            criteriaBuilder.optOption("model_loading_timeout", String.valueOf(config.getModelLoadingTimeout()));
        }
        if (config.getPredictTimeout() > 0) {
            criteriaBuilder.optOption("predict_timeout", String.valueOf(config.getPredictTimeout()));
        }

        Criteria<Input, Output> criteria = criteriaBuilder.build();
        model = criteria.loadModel();
    }

    @Override
    public void setEndpoint(HuggingFaceEndpoint endpoint) {
        this.endpoint = endpoint;
        this.config = endpoint.getConfiguration();
    }

    protected abstract String getPythonScript();

    /**
     * Prepends the configured Hugging Face token to the generated handler as the {@code HF_TOKEN} environment variable
     * so that every task can load gated or private models. {@code transformers.pipeline()} reads {@code HF_TOKEN} from
     * the environment when no explicit token is passed; previously only the chat task passed a token, so the other
     * tasks failed with 401 on gated models. The token comes from the {@code authToken} option or is resolved from an
     * OAuth profile (see {@link org.apache.camel.component.huggingface.HuggingFaceProducer}), both surfaced through
     * {@code config.getAuthToken()}.
     */
    protected String withAuthToken(String pythonScript) {
        String authToken = config.getAuthToken();
        if (authToken == null || authToken.isEmpty()) {
            return pythonScript;
        }
        return "import os\nos.environ['HF_TOKEN'] = '" + authToken.replace("'", "\\'") + "'\n\n" + pythonScript;
    }

    protected String loadPythonScript(String resourcePath, Object... args) {
        InputStream is = null;
        try {
            String fullPath = "classpath:org/apache/camel/component/huggingface/tasks/" + resourcePath;
            is = ResourceHelper.resolveResourceAsInputStream(endpoint.getCamelContext(), fullPath);
            String script = IOHelper.loadText(is);
            return script.formatted(args);
        } catch (IOException e) {
            throw new RuntimeCamelException("Failed to load python script: " + resourcePath, e);
        } finally {
            IOHelper.close(is);
        }
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
        if (config.isPooling()) {
            lock.lock();
            try {
                if (predictor == null) {
                    predictor = model.newPredictor();
                }
                Output output = predictor.predict(input);
                processOutput(exchange, output);
            } finally {
                lock.unlock();
            }
        } else {
            try (Predictor<Input, Output> djlPredictor = model.newPredictor()) {
                Output output = djlPredictor.predict(input);
                processOutput(exchange, output);
            }
        }
    }

    protected abstract Input prepareInput(Exchange exchange) throws Exception;

    protected abstract void processOutput(Exchange exchange, Output output) throws Exception;

    @Override
    public void close() throws Exception {
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
        if (tmpDir != null) {
            Files.walkFileTree(tmpDir, new SimpleFileVisitor<>() {
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
