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
package org.apache.camel.component.wolfdefender;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.semantic.SemanticAdapter;
import org.apache.camel.semantic.SemanticCapabilities;
import org.apache.camel.semantic.SemanticEvaluation;
import org.apache.camel.semantic.SemanticExpert;
import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.apache.camel.semantic.SemanticOperation;
import org.apache.camel.semantic.SemanticParameter;
import org.apache.camel.semantic.SemanticResult;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.service.ServiceSupport;

/** Local, fixed-purpose prompt-injection expert. Configure before starting the Camel service. */
@JdkService("semantic-adapter")
@SemanticExpert(name = "wolf-defender", provider = "wolf-defender", artifactId = "camel-wolf-defender",
                description = "Local Wolf-Defender Small prompt-injection detection",
                operations = @SemanticOperation(name = "injection", description = "Detect prompt injection in text",
                                                inputTypes = InputType.TEXT,
                                                inputRequirements = "Nonblank text within the configured character and token limits",
                                                resultType = ResultType.BOOLEAN,
                                                resultMeaning = "True means prompt injection or jailbreak-like instructions were detected",
                                                probability = true,
                                                probabilityMeaning = "Softmax probability of the INJECTION class (class 1)",
                                                parameters = {
                                                        @SemanticParameter(name = "threshold",
                                                                           description = "Inclusive injection probability threshold",
                                                                           type = Number.class, minimum = 0, maximum = 1,
                                                                           omission = "Use 0.5"),
                                                        @SemanticParameter(name = "uncertainty",
                                                                           description = "Half-width of the uncertainty band around the threshold",
                                                                           type = Number.class, minimum = 0, maximum = 1,
                                                                           omission = "No uncertainty band"),
                                                        @SemanticParameter(name = "uncertaintyPolicy",
                                                                           description = "Behaviour inside the inclusive uncertainty band",
                                                                           values = { "fail", "non-match" },
                                                                           omission = "Fail the evaluation") }))
public class WolfDefenderSemanticAdapter extends ServiceSupport implements SemanticAdapter, CamelContextAware {
    public static final String MODEL_REVISION = "bcab2eff97bcabd7227849639e2d0d7a61b46c92";
    public static final String MODEL_ID = "patronus-studio/wolf-defender-prompt-injection-small";

    private CamelContext camelContext;
    private String modelDirectory;
    private int maxCharacters = 16384;
    private int maxTokens = 2048;
    private int inferenceThreads = 1;
    private long timeoutMillis = 30000;
    private volatile Worker worker;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getModelDirectory() {
        return modelDirectory;
    }

    /** Directory containing the explicitly provisioned pinned FP32 model, tokenizer.json and config.json. */
    public void setModelDirectory(String modelDirectory) {
        this.modelDirectory = modelDirectory;
    }

    public int getMaxCharacters() {
        return maxCharacters;
    }

    /** Maximum UTF-16 code units checked before tokenization. Defaults to 16384. */
    public void setMaxCharacters(int maxCharacters) {
        this.maxCharacters = maxCharacters;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    /** Maximum tokens including special tokens, between 2 and the supported 2048-token window. No truncation. */
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getInferenceThreads() {
        return inferenceThreads;
    }

    /** Positive number of ONNX intra-operation CPU threads. Defaults to one. */
    public void setInferenceThreads(int inferenceThreads) {
        this.inferenceThreads = inferenceThreads;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    /** Evaluation and shutdown waiting limit in milliseconds. Native cancellation is cooperative. */
    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public void validate(SemanticEvaluation definition) {
        SemanticCapabilities.from(getClass()).validate(definition);
        double threshold = threshold(definition);
        double uncertainty = uncertainty(definition);
        if (threshold - uncertainty < 0 || threshold + uncertainty > 1) {
            throw new IllegalArgumentException("Parameters 'threshold' and 'uncertainty' must keep the band within [0,1]");
        }
    }

    @Override
    public void validateInput(SemanticEvaluation evaluation, Object state) {
        Worker current = validateState(evaluation, state);
        try {
            execute(current, (String) state, true);
        } catch (WolfDefenderException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new WolfDefenderException("Input validation failed (" + failure.getClass().getSimpleName() + ")");
        }
    }

    private Worker validateState(SemanticEvaluation evaluation, Object state) {
        SemanticCapabilities.from(getClass()).operation(evaluation.getOperation()).validateInput(state);
        Worker current = worker;
        if (current == null || !isStarted()) {
            throw new WolfDefenderException("Expert service is not started");
        }
        String text = (String) state;
        if (text.length() > current.maxCharacters || text.isBlank()) {
            throw new WolfDefenderException("Require nonblank text within maxCharacters=" + current.maxCharacters);
        }
        return current;
    }

    private static double threshold(SemanticEvaluation evaluation) {
        return ((Number) evaluation.getParameters().getOrDefault("threshold", 0.5)).doubleValue();
    }

    private static double uncertainty(SemanticEvaluation evaluation) {
        return ((Number) evaluation.getParameters().getOrDefault("uncertainty", 0.0)).doubleValue();
    }

    private static boolean decision(SemanticEvaluation evaluation, double probability) {
        if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
            throw new WolfDefenderException("Injection probability must be finite and within [0,1]");
        }
        double threshold = threshold(evaluation);
        double uncertainty = uncertainty(evaluation);
        if (uncertainty > 0 && probability >= threshold - uncertainty && probability <= threshold + uncertainty) {
            if (!"non-match".equals(evaluation.getParameters().get("uncertaintyPolicy"))) {
                throw new WolfDefenderException("Prompt-injection decision is uncertain");
            }
            return false;
        }
        return probability >= threshold;
    }

    @Override
    protected void doStart() throws Exception {
        if (worker != null && !worker.isTerminated()) {
            throw new WolfDefenderException("Previous worker is still stopping");
        }
        if (maxCharacters < 1 || maxTokens < 2 || maxTokens > 2048 || inferenceThreads < 1 || timeoutMillis < 1) {
            throw new WolfDefenderException(
                    "Require positive maxCharacters, inferenceThreads, timeoutMillis and maxTokens in [2,2048]");
        }
        if (modelDirectory == null && camelContext != null) {
            modelDirectory = camelContext.getPropertiesComponent().resolveProperty("camel.wolf-defender.model-directory")
                    .orElse(null);
        }
        WolfDefenderInference inference = null;
        try {
            inference = createInference();
            worker = new Worker(inference, maxCharacters, maxTokens, timeoutMillis);
        } catch (Exception | LinkageError failure) {
            if (inference != null) {
                inference.close();
            }
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (failure instanceof WolfDefenderException known) {
                throw known;
            }
            throw new WolfDefenderException("Model loading failed (" + failure.getClass().getSimpleName() + ")");
        }
    }

    WolfDefenderInference createInference() throws Exception {
        if (modelDirectory == null || modelDirectory.isBlank()) {
            throw new WolfDefenderException("Configure modelDirectory or camel.wolf-defender.model-directory");
        }
        return WolfDefenderInference.load(Path.of(modelDirectory), inferenceThreads);
    }

    @Override
    public SemanticResult evaluate(SemanticEvaluation definition, Object state) throws Exception {
        validate(definition);
        Worker current = validateState(definition, state);
        double probability = execute(current, (String) state, false);
        return new SemanticResult(
                decision(definition, probability), probability, null, null,
                Map.of("provider", "wolf-defender", "model", MODEL_ID, "revision", MODEL_REVISION, "export", "fp32"));
    }

    private Double execute(Worker current, String text, boolean validationOnly) throws Exception {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Wolf-Defender evaluation interrupted");
        }
        var evaluation = new Evaluation(current, text, validationOnly);
        if (!current.active.compareAndSet(null, evaluation)) {
            throw new WolfDefenderException("Expert is busy; only one evaluation per instance is allowed");
        }
        try {
            current.execute(evaluation);
            return evaluation.get(current.timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (TimeoutException timeout) {
            throw new TimeoutException("Wolf-Defender evaluation exceeded timeoutMillis=" + current.timeoutMillis);
        } catch (RejectedExecutionException busy) {
            current.active.compareAndSet(evaluation, null);
            throw new WolfDefenderException("Expert is busy or stopping");
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof WolfDefenderException known) {
                throw known;
            }
            // Native/tokenizer exception messages can contain input; do not retain their cause.
            throw new WolfDefenderException("Inference failed (" + failure.getCause().getClass().getSimpleName() + ")");
        } finally {
            if (!evaluation.isDone()) {
                evaluation.abort();
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        Worker current = worker;
        if (current != null) {
            try {
                var call = current.active.get();
                if (call != null) {
                    call.abort();
                }
            } finally {
                current.shutdownNow();
            }
            if (!current.awaitTermination(current.timeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new WolfDefenderException("Shutdown timed out; native resources will close when the worker exits");
            }
            if (current.closeFailure != null) {
                throw new WolfDefenderException("Native resource cleanup failed (" + current.closeFailure + ")");
            }
        }
    }

    private static final class Evaluation extends CompletableFuture<Double> implements Runnable {
        private final Worker worker;
        private final String text;
        private final boolean validationOnly;
        private final WolfDefenderInference.Call call = new WolfDefenderInference.Call();

        Evaluation(Worker worker, String text, boolean validationOnly) {
            this.worker = worker;
            this.text = text;
            this.validationOnly = validationOnly;
        }

        @Override
        public void run() {
            Double probability = null;
            Throwable failure = null;
            try {
                call.start();
                if (validationOnly) {
                    worker.inference.validateInput(text, worker.maxTokens, call);
                } else {
                    probability = worker.inference.evaluate(text, worker.maxTokens, call);
                }
                call.checkCancelled();
            } catch (Exception | LinkageError e) {
                failure = e;
            } finally {
                call.finish();
                worker.active.compareAndSet(this, null);
            }
            // Release admission before waking the caller. The one-slot executor queue allows the
            // next call to arrive while this worker is returning, without a spurious busy error.
            if (failure == null) {
                complete(probability);
            } else {
                completeExceptionally(failure);
            }
        }

        void abort() throws Exception {
            try {
                call.cancel();
            } finally {
                cancel(false);
            }
        }
    }

    private static final class Worker extends ThreadPoolExecutor {
        private final WolfDefenderInference inference;
        private final int maxCharacters;
        private final int maxTokens;
        private final long timeoutMillis;
        private final AtomicReference<Evaluation> active = new AtomicReference<>();
        private volatile String closeFailure;

        Worker(WolfDefenderInference inference, int maxCharacters, int maxTokens, long timeoutMillis) {
            super(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "Camel-wolf-defender");
                thread.setDaemon(true);
                return thread;
            });
            this.inference = inference;
            this.maxCharacters = maxCharacters;
            this.maxTokens = maxTokens;
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        protected void terminated() {
            // Never free native handles while a timed-out JNI call still uses them.
            try {
                inference.close();
            } catch (Exception failure) {
                closeFailure = failure.getClass().getSimpleName();
            }
        }
    }
}
