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
package org.apache.camel.semantic;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.camel.Expression;
import org.apache.camel.builder.ThreadPoolBuilder;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "semantic-evaluate", displayName = "Semantic Evaluation",
            description = "Evaluate sample input through a semantic definition or expert operation", readOnly = false)
public class SemanticEvaluateConsole extends AbstractDevConsole {
    @Metadata(label = "query", description = "Name of the published evaluation to execute", javaType = "java.lang.String")
    public static final String EVALUATION = "evaluation";
    @Metadata(label = "query", description = "Sample message body", javaType = "java.lang.Object")
    public static final String BODY = "body";
    @Metadata(label = "query", description = "Sample message headers", javaType = "java.util.Map")
    public static final String HEADERS = "headers";
    @Metadata(label = "query", description = "Sample exchange variables", javaType = "java.util.Map")
    public static final String VARIABLES = "variables";
    @Metadata(label = "query", description = "Expert reference for direct evaluation", javaType = "java.lang.String")
    public static final String EXPERT = "expert";
    @Metadata(label = "query", description = "Expert operation for direct evaluation", javaType = "java.lang.String")
    public static final String OPERATION = "operation";
    @Metadata(label = "query", description = "Already-selected input for direct evaluation", javaType = "java.lang.Object")
    public static final String INPUT = "input";
    @Metadata(label = "query", description = "Expert operation parameters", javaType = "java.util.Map")
    public static final String PARAMETERS = "parameters";

    @Metadata(label = "query", description = "Maximum evaluation time in milliseconds (1 to 50000, default 50000)",
              javaType = "java.lang.Long")
    public static final String TIMEOUT = "timeout";

    private ExecutorService executor;
    // A resolver-created console may be called without starting it; only an explicit stop rejects calls.
    private boolean stopped;

    private synchronized ExecutorService executor() throws Exception {
        if (stopped || isStopping()) {
            throw new IllegalStateException("Semantic evaluation console is stopping or stopped");
        }
        if (executor == null) {
            executor = new ThreadPoolBuilder(getCamelContext()).poolSize(2).maxPoolSize(2).maxQueueSize(0)
                    .rejectedPolicy(ThreadPoolRejectedPolicy.Abort).build(this, "SemanticEvaluation");
        }
        return executor;
    }

    @Override
    protected synchronized void doStart() throws Exception {
        stopped = false;
        super.doStart();
    }

    @Override
    protected synchronized void doStop() throws Exception {
        stopped = true;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        super.doStop();
    }

    @Override
    public Object call(MediaType mediaType, Map<String, Object> options) {
        // This console bounds concurrent requests itself; do not queue callers on AbstractDevConsole's lock.
        return mediaType == MediaType.JSON ? doCallJson(options) : doCallText(options);
    }

    public record Response(
            @Metadata(description = "Evaluation name") String evaluation,
            @Metadata(description = "Success or failed") String status,
            @Metadata(description = "Validated result value") Object value,
            @Metadata(description = "Provider probability") Double probability,
            @Metadata(description = "Probabilities by label") Map<String, Double> probabilities,
            @Metadata(description = "Provider confidence") Double confidence,
            @Metadata(description = "Provider result metadata") Map<String, Object> metadata,
            @Metadata(description = "Elapsed time in milliseconds") long elapsedMillis,
            @Metadata(description = "Evaluation error, if any") String error) {
    }

    public SemanticEvaluateConsole() {
        super("camel", "semantic-evaluate", "Semantic Evaluation",
              "Evaluate sample input through a semantic definition or expert operation");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        return doCallJson(options).toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        long start = System.nanoTime();
        Future<Map<String, Object>> task = null;
        String failure;
        try {
            long timeout = optionLong(options, TIMEOUT, 50000);
            if (timeout < 1 || timeout > 50000) {
                throw new IllegalArgumentException("Evaluation timeout must be between 1 and 50000 milliseconds");
            }
            task = executor().submit(() -> evaluate(options));
            return task.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            failure = "Evaluation timed out; cancellation requested";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failure = "Evaluation cancelled";
        } catch (RejectedExecutionException e) {
            failure = "Busy: semantic evaluations are already running";
        } catch (Exception e) {
            failure = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        } finally {
            if (task != null && !task.isDone()) {
                task.cancel(true);
            }
        }
        return JsonRecordSupport.toJsonObject(new Response(
                optionString(options, EVALUATION), "failed", null, null, null, null, null, elapsed(start), failure));
    }

    private Map<String, Object> evaluate(Map<String, Object> options) {
        String name = optionString(options, EVALUATION);
        long start = System.nanoTime();
        try {
            if (options.containsKey(OPERATION)) {
                if (options.containsKey(EVALUATION) || options.containsKey(BODY)
                        || options.containsKey(HEADERS) || options.containsKey(VARIABLES)) {
                    throw new IllegalArgumentException("Choose a named evaluation or a direct expert operation");
                }
                Object supplied = options.getOrDefault(PARAMETERS, Map.of());
                if (!(supplied instanceof Map<?, ?> map) || map.keySet().stream().anyMatch(k -> !(k instanceof String))) {
                    throw new IllegalArgumentException("Expert parameters must be a JSON object with string keys");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> parameters = (Map<String, Object>) map;
                SemanticEvaluation evaluation = new SemanticEvaluation(
                        optionString(options, OPERATION), optionString(options, EXPERT), null, parameters);
                SemanticLanguage language = (SemanticLanguage) getCamelContext().resolveLanguage("semantic");
                SemanticResult result = language.evaluate(evaluation, options.get(INPUT));
                return response(null, result, start);
            }
            if (options.containsKey(EXPERT) || options.containsKey(INPUT) || options.containsKey(PARAMETERS)) {
                throw new IllegalArgumentException("Select an expert operation for direct evaluation");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Select a named semantic evaluation");
            }
            SemanticEvaluations.get(getCamelContext()).get(name);
            var exchange = new DefaultExchange(getCamelContext());
            exchange.getMessage().setBody(options.get(BODY));
            if (options.get(HEADERS) instanceof Map<?, ?> headers) {
                headers.forEach((key, value) -> exchange.getMessage().setHeader(key.toString(), value));
            } else if (options.containsKey(HEADERS)) {
                throw new IllegalArgumentException("Sample headers must be a JSON object");
            }
            if (options.get(VARIABLES) instanceof Map<?, ?> variables) {
                // Repository-qualified variables would write into the running application's shared state.
                if (variables.keySet().stream().anyMatch(key -> !(key instanceof String) || key.toString().contains(":"))) {
                    throw new IllegalArgumentException(
                            "Sample variables must have exchange-local names without a repository prefix");
                }
                variables.forEach((key, value) -> exchange.setVariable(key.toString(), value));
            } else if (options.containsKey(VARIABLES)) {
                throw new IllegalArgumentException("Sample variables must be a JSON object");
            }
            Expression expression = getCamelContext().resolveLanguage("semantic").createExpression("ref:" + name);
            expression.evaluate(exchange, Object.class);
            SemanticResult result = exchange.getProperty(SemanticLanguage.RESULT, SemanticResult.class);
            return response(name, result, start);
        } catch (Exception e) {
            return JsonRecordSupport.toJsonObject(new Response(
                    name, "failed", null, null, null, null, null,
                    elapsed(start), e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    private static Map<String, Object> response(String name, SemanticResult result, long start) {
        return JsonRecordSupport.toJsonObject(new Response(
                name, "success", result.getValue(), result.getProbability(), result.getProbabilities(),
                result.getConfidence(), result.getMetadata(), elapsed(start), null));
    }

    private static long elapsed(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
