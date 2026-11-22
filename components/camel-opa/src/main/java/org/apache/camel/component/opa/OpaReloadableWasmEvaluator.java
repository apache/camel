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
package org.apache.camel.component.opa;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Atomically replaces validated WASM policy/data snapshots while admitted evaluations drain the previous snapshot.
 * Resource loading and policy construction never hold the decision-state lock.
 */
public final class OpaReloadableWasmEvaluator extends OpaPolicyEvaluator implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(OpaReloadableWasmEvaluator.class);

    @FunctionalInterface
    interface BundleLoader {
        OpaWasmEvaluator.Bundle load() throws Exception;
    }

    @FunctionalInterface
    interface EvaluatorFactory {
        OpaWasmEvaluator create(OpaWasmEvaluator.Bundle bundle) throws Exception;
    }

    private final Object state = new Object();
    private final Object reloadLock = new Object();
    private final CamelContext camelContext;
    private final long reloadInterval;
    private final BundleLoader loader;
    private final EvaluatorFactory factory;
    private Snapshot active;
    private boolean closed;
    private boolean reloadEnabled = true;
    private long watchGeneration;
    /** The revision of content that failed to load; a poll does not rebuild it until the content changes. */
    private String lastFailedRevision;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> watch;
    private volatile OpaPolicyReloadStatus status;

    OpaReloadableWasmEvaluator(CamelContext camelContext, long reloadInterval, BundleLoader loader,
                               EvaluatorFactory factory, String policyPath, String allowKey, String includeHeaders,
                               String includeProperties, boolean includeBody, boolean failOpen) throws Exception {
        this(camelContext, reloadInterval, loader, factory, policyPath, allowKey, includeHeaders, includeProperties,
             includeBody, failOpen, null);
    }

    OpaReloadableWasmEvaluator(CamelContext camelContext, long reloadInterval, BundleLoader loader,
                               EvaluatorFactory factory, String policyPath, String allowKey, String includeHeaders,
                               String includeProperties, boolean includeBody, boolean failOpen,
                               Expression inputExpression) throws Exception {
        // the input is built here, by evaluate() of this outer evaluator, so the expression must reach this constructor
        super(policyPath, allowKey, includeHeaders, includeProperties, includeBody, failOpen, inputExpression);
        if (reloadInterval < 0) {
            throw new IllegalArgumentException("policyReloadInterval must not be negative");
        }
        this.camelContext = ObjectHelper.notNull(camelContext, "camelContext");
        this.reloadInterval = reloadInterval;
        this.loader = loader;
        this.factory = factory;
        OpaWasmEvaluator.Bundle bundle = immutableBundle(loader.load());
        String revision = revision(bundle);
        // OpaWasmEvaluator warms its pool and applies the configured entrypoint/data before returning.
        this.active = new Snapshot(factory.create(bundle), revision);
        this.status = new OpaPolicyReloadStatus(revision, Instant.now(), null, null);
    }

    public static OpaReloadableWasmEvaluator create(
            CamelContext camelContext, String policyBundle, String entrypoint, int poolSize, long borrowTimeout,
            long reloadInterval, String policyPath, String allowKey, String includeHeaders, String includeProperties,
            boolean includeBody, boolean failOpen, Expression inputExpression)
            throws Exception {
        OpaWasmEvaluator.validateWasmOptions(policyBundle, poolSize);
        String resolvedEntrypoint = ObjectHelper.isNotEmpty(entrypoint) ? entrypoint : policyPath;
        return new OpaReloadableWasmEvaluator(
                camelContext, reloadInterval,
                () -> OpaWasmEvaluator.loadPolicy(camelContext, policyBundle),
                bundle -> new OpaWasmEvaluator(
                        bundle.wasm(), bundle.data(), resolvedEntrypoint, poolSize, borrowTimeout,
                        policyPath, allowKey, includeHeaders, includeProperties, includeBody, failOpen, inputExpression),
                policyPath, allowKey, includeHeaders, includeProperties, includeBody, failOpen, inputExpression);
    }

    public OpaPolicyReloadStatus getReloadStatus() {
        return status;
    }

    /** Re-reads the configured resource and publishes a validated replacement only when its content changed. */
    public boolean reload() throws Exception {
        long generation;
        synchronized (state) {
            ensureOpen(-1);
            if (!reloadEnabled) {
                throw new IllegalStateException("The WASM policy reload watcher is stopped");
            }
            generation = watchGeneration;
        }
        // an explicit reload tries again even when the same content failed before
        return reload(generation, false);
    }

    private boolean reload(long generation, boolean skipKnownFailure) throws Exception {
        synchronized (reloadLock) {
            synchronized (state) {
                ensureOpen(generation);
            }
            OpaWasmEvaluator replacement = null;
            boolean published = false;
            String revision = null;
            try {
                OpaWasmEvaluator.Bundle loaded = loader.load();
                // Hash and validate one snapshot even when the loader reuses its module buffer.
                loaded = new OpaWasmEvaluator.Bundle(loaded.wasm().clone(), loaded.data());
                revision = revision(loaded);
                synchronized (state) {
                    ensureOpen(generation);
                    if (active.revision.equals(revision)) {
                        // a successful check; the last failure stays visible in the status
                        status = new OpaPolicyReloadStatus(
                                revision, Instant.now(), status.getLastFailedReload(), status.getLastReloadFailureType());
                        return false;
                    }
                    if (skipKnownFailure && revision.equals(lastFailedRevision)) {
                        // the content that failed to load is still there: building it again would fail the same way
                        // and log the same warning on every poll, so wait for it to change
                        LOG.debug("WASM policy {} still has the content that failed to load; waiting for it to change",
                                getPolicyPath());
                        return false;
                    }
                }
                // loaded is already this reload's own copy of the module, so only the data is checked here
                replacement = factory.create(validData(loaded));
                Snapshot previous;
                synchronized (state) {
                    ensureOpen(generation);
                    previous = active;
                    active = new Snapshot(replacement, revision);
                    published = true;
                    previous.retired = true;
                    lastFailedRevision = null;
                    status = new OpaPolicyReloadStatus(
                            revision, Instant.now(), status.getLastFailedReload(), status.getLastReloadFailureType());
                    if (previous.readers != 0) {
                        previous = null;
                    }
                }
                if (previous != null) {
                    previous.evaluator.close();
                }
                return true;
            } catch (Throwable e) {
                // an Error from the module compiler (a LinkageError, say) is a failed load like any other: remembered,
                // shown in the status and rethrown as it is; the previous snapshot keeps deciding either way
                synchronized (state) {
                    if (!closed && (generation < 0 || generation == watchGeneration)) {
                        lastFailedRevision = revision;
                        status = new OpaPolicyReloadStatus(
                                active.revision, status.getLastSuccessfulCheck(),
                                Instant.now(), e.getClass().getName());
                    }
                }
                throw e;
            } finally {
                if (replacement != null && !published) {
                    replacement.close();
                }
            }
        }
    }

    /**
     * Enables reloading and, when {@code policyReloadInterval} is positive, starts the Camel-managed scheduler that
     * polls the configured resource. Safe to call after {@link #stopWatching()} to enable reloads again.
     */
    public void startWatching() {
        synchronized (state) {
            ensureOpen(-1);
            reloadEnabled = true;
            if (reloadInterval == 0 || watch != null) {
                return;
            }
            long generation = ++watchGeneration;
            executor = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "opa-policy-reload");
            try {
                watch = executor.scheduleWithFixedDelay(() -> poll(generation), reloadInterval, reloadInterval,
                        TimeUnit.MILLISECONDS);
            } catch (RuntimeException e) {
                camelContext.getExecutorServiceManager().shutdownNow(executor);
                executor = null;
                throw e;
            }
        }
    }

    /**
     * Disables reloading and cancels the scheduler. After this call {@link #reload()} throws
     * {@link IllegalStateException}, also when no interval was configured, until {@link #startWatching()} is called
     * again.
     */
    public void stopWatching() {
        ScheduledExecutorService stopped;
        synchronized (state) {
            reloadEnabled = false;
            ++watchGeneration;
            if (watch != null) {
                watch.cancel(true);
                watch = null;
            }
            stopped = executor;
            executor = null;
        }
        if (stopped != null) {
            camelContext.getExecutorServiceManager().shutdownNow(stopped);
        }
    }

    private void poll(long generation) {
        try {
            reload(generation, true);
        } catch (Throwable e) {
            // also an Error: a run that lets it escape would cancel every later run of the scheduler, and the watcher
            // would stop for good without a status change or a log line
            synchronized (state) {
                if (!closed && generation == watchGeneration) {
                    LOG.warn("Could not reload WASM policy {}; retaining revision {}. Failure type: {}",
                            getPolicyPath(), active.revision, e.getClass().getName());
                }
            }
        }
    }

    private void ensureOpen(long generation) {
        if (closed || generation >= 0 && (!reloadEnabled || generation != watchGeneration)) {
            throw new IllegalStateException("The WASM policy evaluator or its reload watcher is stopped");
        }
    }

    @Override
    protected Object evaluateDecision(Map<String, Object> input) throws Exception {
        Snapshot snapshot;
        synchronized (state) {
            ensureOpen(-1);
            snapshot = active;
            snapshot.readers++;
        }
        try {
            return snapshot.evaluator.evaluateDecision(input);
        } finally {
            boolean drained;
            synchronized (state) {
                drained = --snapshot.readers == 0 && snapshot.retired;
            }
            if (drained) {
                snapshot.evaluator.close();
            }
        }
    }

    @Override
    protected boolean isDecisionPointUnavailable(Exception failure) {
        return failure instanceof TimeoutException;
    }

    @Override
    public void close() {
        Snapshot drained;
        synchronized (state) {
            if (closed) {
                return;
            }
            closed = true;
            active.retired = true;
            drained = active.readers == 0 ? active : null;
        }
        stopWatching();
        if (drained != null) {
            drained.evaluator.close();
        }
    }

    private static OpaWasmEvaluator.Bundle immutableBundle(OpaWasmEvaluator.Bundle bundle) throws Exception {
        validData(bundle);
        return new OpaWasmEvaluator.Bundle(bundle.wasm().clone(), bundle.data());
    }

    private static OpaWasmEvaluator.Bundle validData(OpaWasmEvaluator.Bundle bundle) throws Exception {
        if (bundle.data() != null) {
            JsonNode data = MAPPER.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bundle.data());
            if (data == null || data.isMissingNode()) {
                throw new IllegalArgumentException("WASM bundle data must be a JSON document");
            }
        }
        return bundle;
    }

    private static String revision(OpaWasmEvaluator.Bundle bundle) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] data = bundle.data() != null ? bundle.data().getBytes(StandardCharsets.UTF_8) : new byte[0];
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bundle.wasm().length).array());
        digest.update(bundle.wasm());
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bundle.data() == null ? -1 : data.length).array());
        digest.update(data);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static final class Snapshot {
        private final OpaWasmEvaluator evaluator;
        private final String revision;
        private int readers;
        private boolean retired;

        private Snapshot(OpaWasmEvaluator evaluator, String revision) {
            this.evaluator = ObjectHelper.notNull(evaluator, "evaluator");
            this.revision = revision;
        }
    }
}
