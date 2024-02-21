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
package org.apache.camel.component.file.strategy;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A file read lock that uses an {@link IdempotentRepository} and {@link FileChangedExclusiveReadLockStrategy changed}
 * as the lock strategy. This allows to plugin and use existing idempotent repositories that for example supports
 * clustering. The other read lock strategies that are using marker files or file locks, are not guaranteed to work in
 * clustered setup with various platform and file systems.
 */
public class FileIdempotentChangedRepositoryReadLockStrategy extends ServiceSupport
        implements GenericFileExclusiveReadLockStrategy<File>, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(FileIdempotentChangedRepositoryReadLockStrategy.class);

    private final FileChangedExclusiveReadLockStrategy changed;
    private GenericFileEndpoint<File> endpoint;
    private LoggingLevel readLockLoggingLevel = LoggingLevel.DEBUG;
    private CamelContext camelContext;
    private IdempotentRepository idempotentRepository;
    private boolean removeOnRollback = true;
    private boolean removeOnCommit;
    private int readLockIdempotentReleaseDelay;
    private boolean readLockIdempotentReleaseAsync;
    private int readLockIdempotentReleaseAsyncPoolSize;
    private ScheduledExecutorService readLockIdempotentReleaseExecutorService;
    private boolean shutdownExecutorService;

    public FileIdempotentChangedRepositoryReadLockStrategy() {
        this.changed = new FileChangedExclusiveReadLockStrategy();
        // no need to use marker file as idempotent ensures exclusive read-lock
        this.changed.setMarkerFiler(false);
        this.changed.setDeleteOrphanLockFiles(false);
    }

    @Override
    public void prepareOnStartup(GenericFileOperations<File> operations, GenericFileEndpoint<File> endpoint) throws Exception {
        this.endpoint = endpoint;
        LOG.info("Using FileIdempotentRepositoryReadLockStrategy: {} on endpoint: {}", idempotentRepository, endpoint);

        changed.prepareOnStartup(operations, endpoint);
    }

    @Override
    public boolean acquireExclusiveReadLock(GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange)
            throws Exception {
        // in clustered mode then another node may have processed the file so we
        // must check here again if the file exists
        File path = file.getFile();
        if (!path.exists()) {
            return false;
        }

        // check if we can begin on this file
        String key = asKey(file);
        boolean answer = false;
        try {
            answer = idempotentRepository.add(exchange, key);
        } catch (Exception e) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Cannot acquire read lock due to {}. Will skip the file: {}", e.getMessage(), file, e);
            }
        }
        if (!answer) {
            // another node is processing the file so skip
            CamelLogger.log(LOG, readLockLoggingLevel, "Cannot acquire read lock. Will skip the file: " + file);
        }

        if (answer) {
            // if we acquired during idempotent then check changed also
            answer = changed.acquireExclusiveReadLock(operations, file, exchange);
            if (!answer) {
                // remove from idempotent as we did not acquire it from changed
                idempotentRepository.remove(exchange, key);
            }
        }
        return answer;
    }

    @Override
    public void releaseExclusiveReadLockOnAbort(
            GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange)
            throws Exception {
        changed.releaseExclusiveReadLockOnAbort(operations, file, exchange);
    }

    @Override
    public void releaseExclusiveReadLockOnRollback(
            GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange)
            throws Exception {
        String key = asKey(file);
        Runnable r = () -> {
            if (removeOnRollback) {
                idempotentRepository.remove(exchange, key);
            } else {
                // okay we should not remove then confirm instead
                idempotentRepository.confirm(exchange, key);
            }

            try {
                changed.releaseExclusiveReadLockOnRollback(operations, file, exchange);
            } catch (Exception e) {
                LOG.warn("Error during releasing exclusive read lock on rollback. This exception is ignored.", e);
            }
        };

        delayOrScheduleLockRelease(r);
    }

    private void delayOrScheduleLockRelease(Runnable r) throws InterruptedException {
        if (readLockIdempotentReleaseDelay > 0 && readLockIdempotentReleaseExecutorService != null) {
            LOG.debug("Scheduling read lock release task to run asynchronous delayed after {} millis",
                    readLockIdempotentReleaseDelay);
            readLockIdempotentReleaseExecutorService.schedule(r, readLockIdempotentReleaseDelay, TimeUnit.MILLISECONDS);
        } else if (readLockIdempotentReleaseDelay > 0) {
            LOG.debug("Delaying read lock release task {} millis", readLockIdempotentReleaseDelay);
            Thread.sleep(readLockIdempotentReleaseDelay);
            r.run();
        } else {
            r.run();
        }
    }

    @Override
    public void releaseExclusiveReadLockOnCommit(
            GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange)
            throws Exception {
        String key = asKey(file);
        Runnable r = () -> {
            if (removeOnCommit) {
                idempotentRepository.remove(exchange, key);
            } else {
                // confirm on commit
                idempotentRepository.confirm(exchange, key);
            }

            try {
                changed.releaseExclusiveReadLockOnCommit(operations, file, exchange);
            } catch (Exception e) {
                LOG.warn("Error during releasing exclusive read lock on rollback. This exception is ignored.", e);
            }
        };

        delayOrScheduleLockRelease(r);
    }

    @Override
    public void setTimeout(long timeout) {
        changed.setTimeout(timeout);
    }

    @Override
    public void setCheckInterval(long checkInterval) {
        changed.setCheckInterval(checkInterval);
    }

    @Override
    public void setReadLockLoggingLevel(LoggingLevel readLockLoggingLevel) {
        this.readLockLoggingLevel = readLockLoggingLevel;
        changed.setReadLockLoggingLevel(readLockLoggingLevel);
    }

    @Override
    public void setMarkerFiler(boolean markerFile) {
        // we do not use marker files
    }

    @Override
    public void setDeleteOrphanLockFiles(boolean deleteOrphanLockFiles) {
        // we do not use marker files
    }

    public void setMinLength(long minLength) {
        changed.setMinLength(minLength);
    }

    public void setMinAge(long minAge) {
        changed.setMinAge(minAge);
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * The idempotent repository to use as the store for the read locks.
     */
    public IdempotentRepository getIdempotentRepository() {
        return idempotentRepository;
    }

    /**
     * The idempotent repository to use as the store for the read locks.
     */
    public void setIdempotentRepository(IdempotentRepository idempotentRepository) {
        this.idempotentRepository = idempotentRepository;
    }

    /**
     * Whether to remove the file from the idempotent repository when doing a rollback.
     * <p/>
     * By default this is true.
     */
    public boolean isRemoveOnRollback() {
        return removeOnRollback;
    }

    /**
     * Whether to remove the file from the idempotent repository when doing a rollback.
     * <p/>
     * By default this is true.
     */
    public void setRemoveOnRollback(boolean removeOnRollback) {
        this.removeOnRollback = removeOnRollback;
    }

    /**
     * Whether to remove the file from the idempotent repository when doing a commit.
     * <p/>
     * By default this is false.
     */
    public boolean isRemoveOnCommit() {
        return removeOnCommit;
    }

    /**
     * Whether to remove the file from the idempotent repository when doing a commit.
     * <p/>
     * By default this is false.
     */
    public void setRemoveOnCommit(boolean removeOnCommit) {
        this.removeOnCommit = removeOnCommit;
    }

    /**
     * Whether to delay the release task for a period of millis.
     */
    public void setReadLockIdempotentReleaseDelay(int readLockIdempotentReleaseDelay) {
        this.readLockIdempotentReleaseDelay = readLockIdempotentReleaseDelay;
    }

    public boolean isReadLockIdempotentReleaseAsync() {
        return readLockIdempotentReleaseAsync;
    }

    /**
     * Whether the delayed release task should be synchronous or asynchronous.
     */
    public void setReadLockIdempotentReleaseAsync(boolean readLockIdempotentReleaseAsync) {
        this.readLockIdempotentReleaseAsync = readLockIdempotentReleaseAsync;
    }

    public int getReadLockIdempotentReleaseAsyncPoolSize() {
        return readLockIdempotentReleaseAsyncPoolSize;
    }

    /**
     * The number of threads in the scheduled thread pool when using asynchronous release tasks.
     */
    public void setReadLockIdempotentReleaseAsyncPoolSize(int readLockIdempotentReleaseAsyncPoolSize) {
        this.readLockIdempotentReleaseAsyncPoolSize = readLockIdempotentReleaseAsyncPoolSize;
    }

    public ScheduledExecutorService getReadLockIdempotentReleaseExecutorService() {
        return readLockIdempotentReleaseExecutorService;
    }

    /**
     * To use a custom and shared thread pool for asynchronous release tasks.
     */
    public void setReadLockIdempotentReleaseExecutorService(ScheduledExecutorService readLockIdempotentReleaseExecutorService) {
        this.readLockIdempotentReleaseExecutorService = readLockIdempotentReleaseExecutorService;
    }

    protected String asKey(GenericFile<File> file) {
        // use absolute file path as default key, but evaluate if an expression
        // key was configured
        String key = file.getAbsoluteFilePath();
        if (endpoint.getIdempotentKey() != null) {
            Exchange dummy = endpoint.createExchange(file);
            key = endpoint.getIdempotentKey().evaluate(dummy, String.class);
        }
        return key;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "camelContext", this);
        ObjectHelper.notNull(idempotentRepository, "idempotentRepository", this);

        if (readLockIdempotentReleaseAsync && readLockIdempotentReleaseExecutorService == null) {
            readLockIdempotentReleaseExecutorService = camelContext.getExecutorServiceManager().newScheduledThreadPool(this,
                    "ReadLockChangedIdempotentReleaseTask",
                    readLockIdempotentReleaseAsyncPoolSize);
            shutdownExecutorService = true;
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (shutdownExecutorService && readLockIdempotentReleaseExecutorService != null) {
            camelContext.getExecutorServiceManager().shutdownGraceful(readLockIdempotentReleaseExecutorService, 30000);
            readLockIdempotentReleaseExecutorService = null;
        }
    }

}
