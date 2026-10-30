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
package org.apache.camel.component.langchain4j.core.rag;

import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;

/**
 * A LangChain4j {@link ContentRetriever} that delegates retrieval to a Camel endpoint (a route, a Kamelet or a
 * component).
 * <p>
 * The query text is sent as the message body; query metadata is set as exchange properties (see
 * {@link CamelContentRetrieverConstants}). The response body is converted by {@link ContentConverter}.
 * <p>
 * Each retriever owns a {@link ProducerTemplate} and a bounded thread pool, both created on start and released on stop:
 * register it with {@link CamelContext#addService(Object)} so the context manages its lifecycle, and configure it
 * before it starts. {@link #retrieve(Query)} runs the exchange on the pool and waits at most
 * {@link #setTimeout(Duration)}; a rejection, a timeout, a route failure or a body that cannot be converted follows the
 * {@link #setOnError(OnError)} policy. {@link #setConcurrency(int)} and {@link #setQueueCapacity(int)} bound the calls,
 * with platform and virtual threads alike.
 * <p>
 * {@link #getRetrievalCount()} and {@link #getFailureCount(RetrievalFailure)} count the calls since the retriever was
 * created, so that a source that keeps failing behind {@link OnError#EMPTY} can be spotted. With camel-management, they
 * are also exposed over JMX on the service MBean of a retriever registered while the context starts.
 */
@ManagedResource(description = "Camel endpoint used as a LangChain4j content retriever")
public class CamelContentRetriever extends ServiceSupport implements ContentRetriever, CamelContextAware {

    public static final int DEFAULT_CONCURRENCY = 2;
    public static final int DEFAULT_QUEUE_CAPACITY = 64;
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private CamelContext camelContext;
    private final String name;
    private final String endpointUri;
    private String documentIdKey;
    private Duration timeout = DEFAULT_TIMEOUT;
    private OnError onError = OnError.FAIL;
    private int maxResults;
    private int maxChars;
    private int concurrency = DEFAULT_CONCURRENCY;
    private int queueCapacity = DEFAULT_QUEUE_CAPACITY;
    // replaced as a whole so retrieve() never sees a template without its endpoint or pool; the volatile write in
    // doStart() also publishes the configuration, which cannot change while started
    private volatile Target target;
    private final LongAdder retrievals = new LongAdder();
    private final Map<RetrievalFailure, LongAdder> failures = new EnumMap<>(RetrievalFailure.class);

    public CamelContentRetriever(CamelContext camelContext, String name, String endpointUri) {
        this.camelContext = ObjectHelper.notNull(camelContext, "camelContext");
        this.name = ObjectHelper.notNull(name, "name");
        this.endpointUri = ObjectHelper.notNull(endpointUri, "endpointUri");
        for (RetrievalFailure failure : RetrievalFailure.values()) {
            failures.put(failure, new LongAdder());
        }
    }

    @ManagedAttribute(description = "Source name")
    public String getName() {
        return name;
    }

    @ManagedAttribute(description = "Endpoint URI", mask = true)
    public String getEndpointUri() {
        return endpointUri;
    }

    /** Number of {@link #retrieve(Query)} calls since the retriever was created. */
    @ManagedAttribute(description = "Number of retrievals")
    public long getRetrievalCount() {
        return retrievals.sum();
    }

    /** Number of retrievals that failed for any reason, whatever {@link OnError} did with them. */
    @ManagedAttribute(description = "Number of failed retrievals")
    public long getFailureCount() {
        return failures.values().stream().mapToLong(LongAdder::sum).sum();
    }

    /** Number of retrievals that failed for the given reason. */
    public long getFailureCount(RetrievalFailure reason) {
        return failures.get(ObjectHelper.notNull(reason, "reason")).sum();
    }

    @ManagedAttribute(description = "Number of failed retrievals by reason")
    public Map<String, Long> getFailureCounts() {
        Map<String, Long> answer = new LinkedHashMap<>();
        failures.forEach((reason, count) -> answer.put(reason.name(), count.sum()));
        return answer;
    }

    public String getDocumentIdKey() {
        return documentIdKey;
    }

    /**
     * Key of the element id: the entry of a {@code Map} element, or the metadata key of a {@code TextSegment} or
     * {@code Content} element; copied to {@value CamelContentRetrieverConstants#METADATA_DOCUMENT_ID}.
     */
    public CamelContentRetriever setDocumentIdKey(String documentIdKey) {
        checkNotStarted();
        this.documentIdKey = documentIdKey;
        return this;
    }

    public Duration getTimeout() {
        return timeout;
    }

    /**
     * How long {@link #retrieve(Query)} waits for the route, time spent in the queue included; 30 seconds by default.
     * {@code null} waits until the route answers, so a hung source blocks the caller.
     */
    public CamelContentRetriever setTimeout(Duration timeout) {
        checkNotStarted();
        if (timeout != null && (timeout.isNegative() || timeout.isZero())) {
            throw new IllegalArgumentException("timeout must be positive: " + timeout);
        }
        this.timeout = timeout;
        return this;
    }

    public OnError getOnError() {
        return onError;
    }

    /** What happens when the route fails, is rejected or times out. */
    public CamelContentRetriever setOnError(OnError onError) {
        checkNotStarted();
        this.onError = ObjectHelper.notNull(onError, "onError");
        return this;
    }

    public int getMaxResults() {
        return maxResults;
    }

    /** Maximum number of {@link Content} items this source contributes; 0, the default, means unlimited. */
    public CamelContentRetriever setMaxResults(int maxResults) {
        checkNotStarted();
        this.maxResults = zeroOrPositive(maxResults, "maxResults");
        return this;
    }

    public int getMaxChars() {
        return maxChars;
    }

    /** Maximum number of characters this source contributes across all items; 0, the default, means unlimited. */
    public CamelContentRetriever setMaxChars(int maxChars) {
        checkNotStarted();
        this.maxChars = zeroOrPositive(maxChars, "maxChars");
        return this;
    }

    public int getConcurrency() {
        return concurrency;
    }

    /**
     * Number of route calls this retriever runs in parallel. Calls beyond it wait in the queue; when that is full too,
     * the call is rejected and follows {@code onError}.
     */
    public CamelContentRetriever setConcurrency(int concurrency) {
        checkNotStarted();
        if (concurrency < 1) {
            throw new IllegalArgumentException("concurrency must be >= 1: " + concurrency);
        }
        this.concurrency = concurrency;
        return this;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    /** Number of calls that may wait for a free thread; beyond it a call is rejected and follows {@code onError}. */
    public CamelContentRetriever setQueueCapacity(int queueCapacity) {
        checkNotStarted();
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be >= 1: " + queueCapacity);
        }
        this.queueCapacity = queueCapacity;
        return this;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        ObjectHelper.notNull(camelContext, "camelContext");
        // addService sets the context again, even on a retriever already started with it
        if (camelContext == this.camelContext) {
            return;
        }
        checkNotStarted();
        this.camelContext = camelContext;
    }

    @Override
    protected void doStart() throws Exception {
        if ((long) concurrency + queueCapacity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    name + ": concurrency + queueCapacity must not exceed " + Integer.MAX_VALUE + ": " + concurrency
                                               + " + " + queueCapacity);
        }
        Endpoint endpoint = CamelContextHelper.getMandatoryEndpoint(camelContext, endpointUri);
        // The bounds are enforced by the semaphores of the Target, not by the pool: with virtual threads the pool is
        // one thread per task, bounded by maxQueueSize only, and it blocks a caller beyond it instead of rejecting.
        // The pool only has to take every admitted call, so its queue is as large as the admission bound. A call that
        // ends frees its slot just before the pool frees its own room, so the pool may hold a caller for that instant.
        ThreadPoolProfile profile = new ThreadPoolProfile(name);
        profile.setPoolSize(concurrency);
        profile.setMaxPoolSize(concurrency);
        profile.setMaxQueueSize(concurrency + queueCapacity);
        // never caller-runs: a saturated source must not run its route on the calling thread
        profile.setRejectedPolicy(ThreadPoolRejectedPolicy.Abort);
        ExecutorService pool = camelContext.getExecutorServiceManager()
                .newThreadPool(this, "CamelContentRetriever[" + name + "]", profile);
        try {
            target = new Target(
                    camelContext.createProducerTemplate(), endpoint, pool, ConcurrentHashMap.newKeySet(),
                    new Semaphore(concurrency + queueCapacity), new Semaphore(concurrency));
        } catch (RuntimeException e) {
            camelContext.getExecutorServiceManager().shutdownNow(pool);
            throw e;
        }
    }

    @Override
    protected void doStop() throws Exception {
        Target t = target;
        target = null;
        if (t == null) {
            return;
        }
        // interrupts the running route calls; a call that ignores the interrupt keeps its caller waiting until the timeout
        camelContext.getExecutorServiceManager().shutdownNow(t.pool());
        // the pool dropped these calls: complete them so their callers follow on-error instead of hanging
        for (RetrievalTask task : t.pending()) {
            task.reject();
        }
        ServiceHelper.stopService(t.template());
    }

    @Override
    public List<Content> retrieve(Query query) {
        Target t = target;
        if (t == null) {
            throw new IllegalStateException(
                    name + " is not started: register it with CamelContext.addService(Object) to start it");
        }

        retrievals.increment();
        Exchange exchange = t.endpoint().createExchange(ExchangePattern.InOut);
        exchange.getMessage().setBody(query.text());
        QueryMetadataMapper.toExchange(query.metadata(), exchange);

        if (!t.slots().tryAcquire()) {
            return fail(RetrievalFailure.REJECTED, "was rejected (executor saturated)", exchange,
                    new RejectedExecutionException(name + ": " + concurrency + " running and " + queueCapacity + " queued"));
        }
        RetrievalTask task = new RetrievalTask(t, exchange);
        t.pending().add(task);
        try {
            t.pool().execute(task);
        } catch (RejectedExecutionException e) {
            t.pending().remove(task);
            task.release();
            return t.pool().isShutdown()
                    ? fail(RetrievalFailure.STOPPED, "is stopping", exchange, e)
                    : fail(RetrievalFailure.REJECTED, "was rejected (executor saturated)", exchange, e);
        }

        CompletableFuture<Exchange> future = task.future;
        try {
            Exchange answer = timeout == null
                    ? future.get()
                    : future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (answer.getException() != null) {
                ContentConverter.discard(answer);
                rethrowVirtualMachineError(answer.getException());
                return fail(RetrievalFailure.ROUTE_FAILURE, "failed", answer, answer.getException());
            }
            try {
                return ContentConverter.toContents(answer, name, documentIdKey, maxResults, maxChars);
            } catch (RuntimeException e) {
                return fail(RetrievalFailure.CONVERSION, "returned a body that cannot be converted to content", answer, e);
            }
        } catch (TimeoutException e) {
            // a call still queued is skipped; one already running keeps running until the source answers
            giveUp(future);
            return fail(RetrievalFailure.TIMEOUT, "timed out after " + timeout, exchange, e);
        } catch (InterruptedException e) {
            // the caller is going away: it cannot use a result, so the interrupt never follows onError
            giveUp(future);
            Thread.currentThread().interrupt();
            throw new CamelExecutionException(name + " was interrupted while waiting for the route", exchange, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            rethrowVirtualMachineError(cause);
            return cause instanceof RejectedExecutionException
                    ? fail(RetrievalFailure.STOPPED, "was stopped", exchange, cause)
                    : fail(RetrievalFailure.ROUTE_FAILURE, "failed", exchange, cause);
        }
    }

    // the answer may have arrived just before the caller gave up: nobody converts it then, so release its body
    private static void giveUp(CompletableFuture<Exchange> future) {
        if (!future.cancel(false) && !future.isCompletedExceptionally()) {
            ContentConverter.discard(future.join());
        }
    }

    // the JVM is in trouble: never turn it into a warning and an empty result (CAMEL-27515)
    private static void rethrowVirtualMachineError(Throwable cause) {
        VirtualMachineError error = ObjectHelper.getVirtualMachineError(cause);
        if (error != null) {
            throw error;
        }
    }

    private void checkNotStarted() {
        if (isStartingOrStarted()) {
            throw new IllegalStateException(name + " is started: configure it before it starts");
        }
    }

    private static int zeroOrPositive(int value, String what) {
        if (value >= 0) {
            return value;
        }
        throw new IllegalArgumentException(what + " must be >= 0, 0 for unlimited: " + value);
    }

    private List<Content> fail(RetrievalFailure reason, String what, Exchange exchange, Throwable cause) {
        failures.get(reason).increment();
        return onError.failure(name, reason, what, exchange, cause);
    }

    @Override
    public String toString() {
        return "CamelContentRetriever[" + name + " -> " + URISupport.sanitizeUri(endpointUri) + "]";
    }

    // pending: calls the pool has not started yet, completed by doStop() when the pool drops them.
    // slots: admission, held by a call from retrieve() until it ends, running or queued.
    // running: held while the route runs. With platform threads the pool size already bounds it; with virtual threads
    // a call waits for it on its own thread, which is the queue.
    private record Target(
            ProducerTemplate template, Endpoint endpoint, ExecutorService pool, Set<RetrievalTask> pending,
            Semaphore slots, Semaphore running) {
    }

    // Whoever claims the task first owns its outcome: the pool thread that runs it, or doStop() that rejects it. A
    // caller that gave up completes the future first, so the pool thread skips the call.
    private static final class RetrievalTask implements Runnable {
        private final Target target;
        private final Exchange exchange;
        private final CompletableFuture<Exchange> future = new CompletableFuture<>();
        private final AtomicBoolean claimed = new AtomicBoolean();
        private final AtomicBoolean released = new AtomicBoolean();

        RetrievalTask(Target target, Exchange exchange) {
            this.target = target;
            this.exchange = exchange;
        }

        @Override
        public void run() {
            target.pending().remove(this);
            try {
                try {
                    target.running().acquire();
                } catch (InterruptedException e) {
                    // the retriever stopped while the call waited for its turn
                    Thread.currentThread().interrupt();
                    reject();
                    return;
                }
                try {
                    // the caller gave up (timeout, interrupt) while the call was queued: do not call the source
                    if (future.isDone() || !claimed.compareAndSet(false, true)) {
                        return;
                    }
                    try {
                        Exchange answer = target.template().send(target.endpoint(), exchange);
                        // the caller gave up while the route ran: nobody converts the answer
                        if (!future.complete(answer)) {
                            ContentConverter.discard(answer);
                        }
                    } catch (Throwable e) {
                        // the caller rethrows a VirtualMachineError
                        future.completeExceptionally(e);
                    }
                } finally {
                    target.running().release();
                }
            } finally {
                release();
            }
        }

        void reject() {
            target.pending().remove(this);
            if (claimed.compareAndSet(false, true)) {
                future.completeExceptionally(
                        new RejectedExecutionException("the retriever stopped before the call reached the route"));
            }
            release();
        }

        void release() {
            if (released.compareAndSet(false, true)) {
                target.slots().release();
            }
        }
    }
}
