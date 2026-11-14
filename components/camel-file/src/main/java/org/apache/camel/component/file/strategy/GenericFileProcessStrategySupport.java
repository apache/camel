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
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.component.file.FileConstants;
import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.GenericFileProcessStrategy;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for implementations of {@link GenericFileProcessStrategy}.
 */
public abstract class GenericFileProcessStrategySupport<T> extends ServiceSupport
        implements GenericFileProcessStrategy<T>, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(GenericFileProcessStrategySupport.class);

    protected GenericFileExclusiveReadLockStrategy<T> exclusiveReadLockStrategy;
    protected CamelContext camelContext;
    // files already reported as skipped as their move target resolves outside the starting directory (bounded)
    private final Map<String, Boolean> skippedMoveTargetOutsideStartingDirectory = LRUCacheFactory.newLRUCache(1000);

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void prepareOnStartup(GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint) throws Exception {
        if (exclusiveReadLockStrategy != null) {
            exclusiveReadLockStrategy.prepareOnStartup(operations, endpoint);
        }
    }

    @Override
    public boolean begin(
            GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file)
            throws Exception {
        // if we use exclusive read then acquire the exclusive read (waiting
        // until we got it)
        if (exclusiveReadLockStrategy != null) {
            boolean lock = exclusiveReadLockStrategy.acquireExclusiveReadLock(operations, file, exchange);
            if (!lock) {
                // do not begin since we could not get the exclusive read lock
                return false;
            }
        }

        return true;
    }

    @Override
    public void abort(
            GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file)
            throws Exception {
        deleteLocalWorkFile(exchange);
        operations.releaseRetrievedFileResources(exchange);

        // must release lock last
        if (exclusiveReadLockStrategy != null) {
            exclusiveReadLockStrategy.releaseExclusiveReadLockOnAbort(operations, file, exchange);
        }
    }

    @Override
    public void commit(
            GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file)
            throws Exception {
        deleteLocalWorkFile(exchange);
        operations.releaseRetrievedFileResources(exchange);

        // must release lock last
        if (exclusiveReadLockStrategy != null) {
            exclusiveReadLockStrategy.releaseExclusiveReadLockOnCommit(operations, file, exchange);
        }
    }

    @Override
    public void rollback(
            GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file)
            throws Exception {
        deleteLocalWorkFile(exchange);
        operations.releaseRetrievedFileResources(exchange);

        // must release lock last
        if (exclusiveReadLockStrategy != null) {
            exclusiveReadLockStrategy.releaseExclusiveReadLockOnRollback(operations, file, exchange);
        }
    }

    public GenericFileExclusiveReadLockStrategy<T> getExclusiveReadLockStrategy() {
        return exclusiveReadLockStrategy;
    }

    public void setExclusiveReadLockStrategy(GenericFileExclusiveReadLockStrategy<T> exclusiveReadLockStrategy) {
        this.exclusiveReadLockStrategy = exclusiveReadLockStrategy;
    }

    protected GenericFile<T> renameFile(GenericFileOperations<T> operations, GenericFile<T> from, GenericFile<T> to) {
        // the operations refuse a target that resolves outside the starting directory (see
        // GenericFileOperations.isWithinStartingDirectory) before anything is deleted, created or renamed

        // deleting any existing files before renaming
        try {
            operations.deleteFile(to.getAbsoluteFilePath());
        } catch (GenericFileOperationFailedException e) {
            // ignore the file does not exist
        }

        // make parent folder if missing
        boolean mkdir = operations.buildDirectory(to.getParent(), to.isAbsolute());

        if (!mkdir) {
            throw new GenericFileOperationFailedException(
                    "Cannot create directory: " + to.getParent() + " (could be because of denied permissions)");
        }

        LOG.debug("Renaming file: {} to: {}", from, to);
        boolean renamed = operations.renameFile(from.getAbsoluteFilePath(), to.getAbsoluteFilePath());
        if (!renamed) {
            throw new GenericFileOperationFailedException("Cannot rename file: " + from + " to: " + to);
        }

        return to;
    }

    /**
     * Evaluates the targets the file is moved to before the file is processed, and checks them with
     * {@link GenericFileOperations#isWithinStartingDirectory(String)}: the preMove target, then the move and moveFailed
     * targets of the file where preMove puts it. A file with a target that is refused is skipped, rather than processed
     * and then failing its move, which would leave the file in place to be consumed again on every poll. A target that
     * cannot be evaluated yet is checked again when the file is moved. Only done for the local file endpoint with
     * jailStartingDirectory enabled.
     *
     * @return <tt>true</tt> to process the file, <tt>false</tt> to skip it
     */
    protected boolean acceptMoveTargets(
            GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file,
            GenericFileRenamer<T> beginRenamer, GenericFileRenamer<T> commitRenamer, GenericFileRenamer<T> failureRenamer) {
        if (!endpoint.isJailStartingDirectory() || !(endpoint instanceof FileEndpoint)) {
            // only the local file operations resolve symbolic links, so do not evaluate the targets for nothing
            return true;
        }
        GenericFile<T> current = file;
        if (beginRenamer != null) {
            GenericFile<T> to = evaluateMoveTarget(operations, endpoint, exchange, file, beginRenamer);
            if (to == null) {
                // the move and moveFailed targets depend on the preMove target
                return true;
            }
            if (!acceptMoveTarget(operations, endpoint, file, to)) {
                return false;
            }
            current = to;
        }
        return evaluateAndAcceptMoveTarget(operations, endpoint, exchange, file, current, commitRenamer)
                && evaluateAndAcceptMoveTarget(operations, endpoint, exchange, file, current, failureRenamer);
    }

    private boolean evaluateAndAcceptMoveTarget(
            GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file,
            GenericFile<T> current, GenericFileRenamer<T> renamer) {
        if (renamer == null) {
            return true;
        }
        GenericFile<T> to = evaluateMoveTarget(operations, endpoint, exchange, current, renamer);
        return to == null || acceptMoveTarget(operations, endpoint, file, to);
    }

    private GenericFile<T> evaluateMoveTarget(
            GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, Exchange exchange, GenericFile<T> file,
            GenericFileRenamer<T> renamer) {
        try {
            // evaluate with a copy, the same way as when the file is moved
            Exchange copy = ExchangeHelper.createCopy(exchange, true);
            if (endpoint instanceof FileEndpoint fe) {
                file.bindToExchange(copy, fe.isProbeContentType());
            } else {
                file.bindToExchange(copy);
            }
            copy.getMessage().setMessageId(exchange.getMessage().getMessageId());
            copy.setExchangeId(exchange.getExchangeId());
            return renamer.renameFile(operations, copy, file);
        } catch (Exception e) {
            LOG.debug("Cannot evaluate the move target of file: {} before it is processed due to: {}", file, e.getMessage());
            return null;
        }
    }

    private boolean acceptMoveTarget(
            GenericFileOperations<T> operations, GenericFileEndpoint<T> endpoint, GenericFile<T> file, GenericFile<T> to) {
        String target = to.getAbsoluteFilePath();
        if (operations.isWithinStartingDirectory(target)) {
            return true;
        }
        String source = file.getAbsoluteFilePath();
        String message = "Skipping file: {} as the target it would be moved to: {} resolves outside the starting directory: {}";
        // such a file stays where it is and is listed again on every poll, so only warn the first time
        if (skippedMoveTargetOutsideStartingDirectory.put(source, Boolean.TRUE) == null) {
            LOG.warn(message, source, target, endpoint.getConfiguration().getDirectory());
        } else {
            LOG.debug(message, source, target, endpoint.getConfiguration().getDirectory());
        }
        return false;
    }

    protected void deleteLocalWorkFile(Exchange exchange) {
        // delete local work file, if it was used (eg by ftp component)
        String local = exchange.getIn().getHeader(FileConstants.FILE_LOCAL_WORK_PATH, String.class);
        if (local != null) {
            File f = new File(local);
            if (f.exists()) {
                boolean deleted = FileUtil.deleteFile(f);
                LOG.trace("Local work file: {} was deleted: {}", local, deleted);
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (exclusiveReadLockStrategy instanceof CamelContextAware camelContextAware) {
            camelContextAware.setCamelContext(camelContext);
        }
        ServiceHelper.startService(exclusiveReadLockStrategy);
    }

    @Override
    protected void doStop() throws Exception {
        skippedMoveTargetOutsideStartingDirectory.clear();
        ServiceHelper.stopService(exclusiveReadLockStrategy);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(exclusiveReadLockStrategy);
    }
}
