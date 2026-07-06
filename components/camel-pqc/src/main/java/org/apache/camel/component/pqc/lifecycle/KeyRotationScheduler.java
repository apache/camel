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
package org.apache.camel.component.pqc.lifecycle;

import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An opt-in background scheduler that periodically evaluates the keys managed by a {@link KeyLifecycleManager} and
 * rotates the ones that need rotation according to a configurable policy (maximum age and/or maximum usage). A rotated
 * key is replaced by a freshly generated key while the previous key is deprecated - this is the behaviour of
 * {@link KeyLifecycleManager#rotateKey(String, String, String)}.
 * <p/>
 * The scheduler is inactive until {@link #start()} is called. It implements the Camel {@code Service} contract, so it
 * can be added to a {@code CamelContext} to participate in the context lifecycle:
 *
 * <pre>{@code
 * KeyRotationScheduler scheduler = new KeyRotationScheduler(keyManager)
 *         .setCheckInterval(Duration.ofHours(6))
 *         .setMaxKeyAge(Duration.ofDays(90))
 *         .setMaxKeyUsage(1_000_000);
 * camelContext.addService(scheduler); // started and stopped with the context
 * }</pre>
 *
 * On every tick the scheduler lists the managed keys, keeps only those matching the configured {@link #setKeyFilter key
 * filter} (by default only {@link KeyMetadata.KeyStatus#ACTIVE ACTIVE} keys) and, for each of them, delegates the
 * decision to {@link KeyLifecycleManager#needsRotation(String, Duration, long)}. When a key needs rotation, the id of
 * its replacement is produced by the configured {@link #setKeyIdStrategy key id strategy} (by default the previous key
 * id with a rotation marker and timestamp appended).
 * <p/>
 * At least one rotation signal should be configured (a {@link #setMaxKeyAge maximum age}, a {@link #setMaxKeyUsage
 * maximum usage}, or a per-key {@code nextRotationAt}/{@code PENDING_ROTATION} status recorded in the key metadata);
 * otherwise no key will ever be selected for rotation.
 * <p/>
 * A single check can also be triggered on demand with {@link #checkAndRotate()}, which is what the scheduled task
 * calls. The method is thread-safe: concurrent invocations (for example a manual call overlapping a scheduled tick) are
 * serialized.
 */
public class KeyRotationScheduler extends ServiceSupport {

    private static final Logger LOG = LoggerFactory.getLogger(KeyRotationScheduler.class);

    private final KeyLifecycleManager keyManager;

    private Duration checkInterval = Duration.ofHours(1);
    private Duration maxKeyAge;
    private long maxKeyUsage;
    private Predicate<KeyMetadata> keyFilter = metadata -> metadata.getStatus() == KeyMetadata.KeyStatus.ACTIVE;
    private Function<KeyMetadata, String> keyIdStrategy = KeyRotationScheduler::defaultRotatedKeyId;
    private KeyRotationListener listener;

    private final AtomicLong checksPerformed = new AtomicLong();
    private final AtomicLong rotationsPerformed = new AtomicLong();
    private final AtomicLong rotationFailures = new AtomicLong();
    private volatile Instant lastCheckAt;

    private ScheduledExecutorService executorService;

    public KeyRotationScheduler(KeyLifecycleManager keyManager) {
        this.keyManager = ObjectHelper.notNull(keyManager, "keyManager");
    }

    /**
     * Performs a single rotation pass over the managed keys and returns how many keys were rotated. This is invoked by
     * the scheduled task and can also be called manually. Never throws for an individual key failure: such failures are
     * counted, logged and reported to the {@link KeyRotationListener listener}, then the pass continues with the next
     * key.
     *
     * @return the number of keys rotated during this pass
     */
    public synchronized int checkAndRotate() {
        checksPerformed.incrementAndGet();
        lastCheckAt = Instant.now();

        List<KeyMetadata> keys;
        try {
            keys = keyManager.listKeys();
        } catch (Exception e) {
            LOG.warn("Failed to list keys during rotation check", e);
            notifyError(null, e);
            return 0;
        }

        int rotated = 0;
        for (KeyMetadata metadata : keys) {
            if (metadata == null || !keyFilter.test(metadata)) {
                continue;
            }
            String keyId = metadata.getKeyId();
            try {
                if (keyManager.needsRotation(keyId, maxKeyAge, maxKeyUsage)) {
                    String newKeyId = keyIdStrategy.apply(metadata);
                    LOG.info("Rotating PQC key '{}' -> '{}' (algorithm={})", keyId, newKeyId, metadata.getAlgorithm());
                    KeyPair newKeyPair = keyManager.rotateKey(keyId, newKeyId, metadata.getAlgorithm());
                    rotationsPerformed.incrementAndGet();
                    rotated++;
                    notifyRotated(keyId, newKeyId, metadata, newKeyPair);
                }
            } catch (Exception e) {
                rotationFailures.incrementAndGet();
                LOG.warn("Failed to rotate PQC key '{}'", keyId, e);
                notifyError(keyId, e);
            }
        }
        return rotated;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(checkInterval, "checkInterval");
        if (checkInterval.isNegative() || checkInterval.isZero()) {
            throw new IllegalArgumentException("checkInterval must be a positive duration");
        }
        long millis = checkInterval.toMillis();
        executorService = Executors.newSingleThreadScheduledExecutor(new RotationThreadFactory());
        executorService.scheduleWithFixedDelay(this::runScheduledCheck, millis, millis, TimeUnit.MILLISECONDS);
        LOG.info("Started PQC KeyRotationScheduler (interval={}, maxAge={}, maxUsage={})",
                checkInterval, maxKeyAge, maxKeyUsage);
    }

    @Override
    protected void doStop() throws Exception {
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        LOG.info("Stopped PQC KeyRotationScheduler (checks={}, rotations={}, failures={})",
                checksPerformed.get(), rotationsPerformed.get(), rotationFailures.get());
    }

    private void runScheduledCheck() {
        try {
            checkAndRotate();
        } catch (Throwable t) {
            // Swallow everything so a single failing pass does not cancel the recurring schedule
            LOG.warn("Unexpected error during scheduled PQC key rotation check", t);
        }
    }

    private void notifyRotated(String oldKeyId, String newKeyId, KeyMetadata previousMetadata, KeyPair newKeyPair) {
        if (listener != null) {
            try {
                listener.onRotated(oldKeyId, newKeyId, previousMetadata, newKeyPair);
            } catch (Exception e) {
                LOG.warn("KeyRotationListener.onRotated threw an exception", e);
            }
        }
    }

    private void notifyError(String keyId, Exception error) {
        if (listener != null) {
            try {
                listener.onError(keyId, error);
            } catch (Exception e) {
                LOG.warn("KeyRotationListener.onError threw an exception", e);
            }
        }
    }

    private static String defaultRotatedKeyId(KeyMetadata metadata) {
        return metadata.getKeyId() + "-rotated-" + Instant.now().toEpochMilli();
    }

    public KeyLifecycleManager getKeyManager() {
        return keyManager;
    }

    public Duration getCheckInterval() {
        return checkInterval;
    }

    /**
     * How often the managed keys are evaluated for rotation. Must be a positive duration. Defaults to one hour.
     */
    public KeyRotationScheduler setCheckInterval(Duration checkInterval) {
        this.checkInterval = ObjectHelper.notNull(checkInterval, "checkInterval");
        return this;
    }

    public Duration getMaxKeyAge() {
        return maxKeyAge;
    }

    /**
     * Rotate a key once it is older than this age. When {@code null} (the default) age is not used as a rotation
     * signal.
     */
    public KeyRotationScheduler setMaxKeyAge(Duration maxKeyAge) {
        this.maxKeyAge = maxKeyAge;
        return this;
    }

    public long getMaxKeyUsage() {
        return maxKeyUsage;
    }

    /**
     * Rotate a key once its recorded usage count reaches this value. When {@code 0} or negative (the default) usage is
     * not used as a rotation signal.
     */
    public KeyRotationScheduler setMaxKeyUsage(long maxKeyUsage) {
        this.maxKeyUsage = maxKeyUsage;
        return this;
    }

    public Predicate<KeyMetadata> getKeyFilter() {
        return keyFilter;
    }

    /**
     * Selects which keys are considered for rotation. Defaults to only {@link KeyMetadata.KeyStatus#ACTIVE ACTIVE}
     * keys.
     */
    public KeyRotationScheduler setKeyFilter(Predicate<KeyMetadata> keyFilter) {
        this.keyFilter = ObjectHelper.notNull(keyFilter, "keyFilter");
        return this;
    }

    public Function<KeyMetadata, String> getKeyIdStrategy() {
        return keyIdStrategy;
    }

    /**
     * Produces the id of the replacement key from the metadata of the key being rotated. Defaults to the previous key
     * id with a rotation marker and timestamp appended.
     */
    public KeyRotationScheduler setKeyIdStrategy(Function<KeyMetadata, String> keyIdStrategy) {
        this.keyIdStrategy = ObjectHelper.notNull(keyIdStrategy, "keyIdStrategy");
        return this;
    }

    public KeyRotationListener getListener() {
        return listener;
    }

    /**
     * An optional listener notified on every rotation and on every rotation error.
     */
    public KeyRotationScheduler setListener(KeyRotationListener listener) {
        this.listener = listener;
        return this;
    }

    /**
     * The number of rotation passes performed so far (scheduled and manual).
     */
    public long getChecksPerformed() {
        return checksPerformed.get();
    }

    /**
     * The number of keys successfully rotated so far.
     */
    public long getRotationsPerformed() {
        return rotationsPerformed.get();
    }

    /**
     * The number of key rotation attempts that failed so far.
     */
    public long getRotationFailures() {
        return rotationFailures.get();
    }

    /**
     * The instant of the last rotation pass, or {@code null} if no pass has run yet.
     */
    public Instant getLastCheckAt() {
        return lastCheckAt;
    }

    /**
     * Listener callbacks invoked by a {@link KeyRotationScheduler}. All methods have an empty default implementation,
     * so an implementation only needs to override the callbacks it cares about.
     */
    public interface KeyRotationListener {

        /**
         * Invoked after a key has been rotated.
         *
         * @param oldKeyId         the id of the key that was rotated (and is now deprecated)
         * @param newKeyId         the id of the freshly generated replacement key
         * @param previousMetadata the metadata of the rotated key at the time of rotation
         * @param newKeyPair       the freshly generated key pair
         */
        default void onRotated(String oldKeyId, String newKeyId, KeyMetadata previousMetadata, KeyPair newKeyPair) {
        }

        /**
         * Invoked when a rotation attempt fails.
         *
         * @param keyId the id of the key that could not be rotated, or {@code null} when listing the keys failed
         * @param error the failure
         */
        default void onError(String keyId, Exception error) {
        }
    }

    private static final class RotationThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "PQCKeyRotationScheduler");
            thread.setDaemon(true);
            return thread;
        }
    }
}
