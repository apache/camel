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
 * A file read lock that uses an {@link IdempotentRepository} and {@link FileRenameExclusiveReadLockStrategy rename} as
 * the lock strategy. This allows to plugin and use existing idempotent repositories that for example supports
 * clustering. The other read lock strategies that are using marker files or file locks, are not guaranteed to work in
 * clustered setup with various platform and file systems.
 */
public class FileIdempotentRenameRepositoryReadLockStrategy extends ServiceSupport
        implements GenericFileExclusiveReadLockStrategy<File>, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(FileIdempotentRenameRepositoryReadLockStrategy.class);
    private final FileRenameExclusiveReadLockStrategy rename;
    private GenericFileEndpoint<File> endpoint;
    private LoggingLevel readLockLoggingLevel = LoggingLevel.DEBUG;
    private CamelContext camelContext;
    private IdempotentRepository idempotentRepository;
    private boolean removeOnRollback = true;
    private boolean removeOnCommit;

    public FileIdempotentRenameRepositoryReadLockStrategy() {
        this.rename = new FileRenameExclusiveReadLockStrategy();
        // no need to use marker file as idempotent ensures exclusive read-lock
        this.rename.setMarkerFiler(false);
        this.rename.setDeleteOrphanLockFiles(false);
    }

    @Override
    public void prepareOnStartup(GenericFileOperations<File> operations, GenericFileEndpoint<File> endpoint) throws Exception {
        this.endpoint = endpoint;
        LOG.info("Using FileIdempotentRepositoryReadLockStrategy: {} on endpoint: {}", idempotentRepository, endpoint);

        rename.prepareOnStartup(operations, endpoint);
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
            // if we acquired during idempotent then check rename also
            answer = rename.acquireExclusiveReadLock(operations, file, exchange);
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
        rename.releaseExclusiveReadLockOnAbort(operations, file, exchange);
    }

    @Override
    public void releaseExclusiveReadLockOnRollback(
            GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange)
            throws Exception {
        String key = asKey(file);
        if (removeOnRollback) {
            idempotentRepository.remove(exchange, key);
        } else {
            // okay we should not remove then confirm it instead
            idempotentRepository.confirm(exchange, key);
        }

        rename.releaseExclusiveReadLockOnRollback(operations, file, exchange);
    }

    @Override
    public void releaseExclusiveReadLockOnCommit(
            GenericFileOperations<File> operations, GenericFile<File> file, Exchange exchange)
            throws Exception {
        String key = asKey(file);
        if (removeOnCommit) {
            idempotentRepository.remove(exchange, key);
        } else {
            // confirm on commit
            idempotentRepository.confirm(exchange, key);
        }

        rename.releaseExclusiveReadLockOnCommit(operations, file, exchange);
    }

    @Override
    public void setTimeout(long timeout) {
        rename.setTimeout(timeout);
    }

    @Override
    public void setCheckInterval(long checkInterval) {
        rename.setCheckInterval(checkInterval);
    }

    @Override
    public void setReadLockLoggingLevel(LoggingLevel readLockLoggingLevel) {
        this.readLockLoggingLevel = readLockLoggingLevel;
        rename.setReadLockLoggingLevel(readLockLoggingLevel);
    }

    @Override
    public void setMarkerFiler(boolean markerFile) {
        // we do not use marker files
    }

    @Override
    public void setDeleteOrphanLockFiles(boolean deleteOrphanLockFiles) {
        // we do not use marker files
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

        // ensure the idempotent repository is added as a service so
        // CamelContext will stop the repo when it shutdown itself
        camelContext.addService(idempotentRepository, true);
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

}
