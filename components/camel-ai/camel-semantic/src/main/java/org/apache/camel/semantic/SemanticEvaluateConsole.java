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

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Expression;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.internal.SemanticAuditService;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.ThreadPoolProfile;
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

    private final AtomicInteger active = new AtomicInteger();
    private ExecutorService executor;
    // A resolver-created console may be called without starting it; only an explicit stop rejects calls.
    private volatile boolean stopped;

    private boolean tryAcquire() {
        while (true) {
            int count = active.get();
            if (count >= 2) {
                return false;
            }
            if (active.compareAndSet(count, count + 1)) {
                return true;
            }
        }
    }

    private void release() {
        active.decrementAndGet();
    }

    private synchronized ExecutorService executor() throws Exception {
        if (stopped || isStopping()) {
            throw new IllegalStateException("Semantic evaluation console is stopping or stopped");
        }
        if (executor == null) {
            ThreadPoolProfile profile = new ThreadPoolProfile();
            profile.setPoolSize(2);
            profile.setMaxPoolSize(2);
            profile.setMaxQueueSize(2);
            profile.setAllowCoreThreadTimeOut(false);
            profile.setKeepAliveTime(0L);
            profile.setTimeUnit(TimeUnit.MILLISECONDS);
            profile.setRejectedPolicy(ThreadPoolRejectedPolicy.Abort);
            executor = getCamelContext().getExecutorServiceManager().newThreadPool(this, "SemanticEvaluation", profile);
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
            List<Runnable> dropped = getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            if (dropped != null) {
                for (Runnable r : dropped) {
                    if (r instanceof Future<?> f) {
                        f.cancel(true);
                    }
                }
            }
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
        SemanticAuditService audit = SemanticAuditService.get(getCamelContext());
        SemanticAuditService.Request request
                = audit.request(options.containsKey(OPERATION) ? "direct_sample" : "definition_sample");
        String failure;
        AtomicBoolean claimed = new AtomicBoolean();
        boolean acquired = false;
        ExecutorService exec = null;
        try {
            long timeout = optionLong(options, TIMEOUT, 50000);
            if (timeout < 1 || timeout > 50000) {
                throw new IllegalArgumentException("Evaluation timeout must be between 1 and 50000 milliseconds");
            }
            long deadline = start + TimeUnit.MILLISECONDS.toNanos(timeout);
            if (stopped || isStopping()) {
                throw new IllegalStateException("Semantic evaluation console is stopping or stopped");
            }
            if (!tryAcquire()) {
                throw new RejectedExecutionException("Busy: semantic evaluations are already running");
            }
            acquired = true;
            exec = executor();
            Callable<Map<String, Object>> callable = () -> {
                if (!claimed.compareAndSet(false, true)) {
                    throw new CancellationException("Evaluation cancelled");
                }
                try {
                    if (System.nanoTime() > deadline) {
                        throw new TimeoutException("Evaluation timed out; cancellation requested");
                    }
                    return evaluate(options, request);
                } finally {
                    release();
                }
            };
            while (true) {
                try {
                    task = exec.submit(callable);
                    break;
                } catch (RejectedExecutionException e) {
                    if (exec.isShutdown()) {
                        throw new CancellationException("Evaluation cancelled");
                    }
                    if (System.nanoTime() >= deadline) {
                        throw new TimeoutException("Evaluation timed out; cancellation requested");
                    }
                    TimeUnit.MICROSECONDS.sleep(100);
                }
            }
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new TimeoutException();
            }
            return task.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            audit.requestOutcome(request, "timeout_cancellation_requested");
            failure = "Evaluation timed out; cancellation requested";
        } catch (CancellationException e) {
            audit.requestOutcome(request, "cancellation_requested");
            failure = "Evaluation cancelled";
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof TimeoutException) {
                audit.requestOutcome(request, "timeout_cancellation_requested");
                failure = "Evaluation timed out; cancellation requested";
            } else if (cause instanceof CancellationException) {
                audit.requestOutcome(request, "cancellation_requested");
                failure = "Evaluation cancelled";
            } else {
                failure = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            audit.requestOutcome(request, "cancellation_requested");
            failure = "Evaluation cancelled";
        } catch (RejectedExecutionException e) {
            failure = "Busy: semantic evaluations are already running";
        } catch (Exception e) {
            failure = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        } finally {
            if (task != null && !task.isDone()) {
                task.cancel(true);
            }
            if (task instanceof Runnable r && exec instanceof ThreadPoolExecutor tpe) {
                tpe.remove(r);
            }
            if (acquired && claimed.compareAndSet(false, true)) {
                release();
            }
        }
        return JsonRecordSupport.toJsonObject(new Response(
                optionString(options, EVALUATION), "failed", null, null, null, null, null, elapsed(start), failure));
    }

    private Map<String, Object> evaluate(Map<String, Object> options, SemanticAuditService.Request request) {
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
                SemanticResult result = language.evaluate(evaluation, options.get(INPUT), request);
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
            exchange.setProperty(SemanticAuditService.REQUEST, request);
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
