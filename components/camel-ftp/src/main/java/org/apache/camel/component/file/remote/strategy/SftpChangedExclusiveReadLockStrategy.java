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
package org.apache.camel.component.file.remote.strategy;

import java.time.Duration;

import com.jcraft.jsch.ChannelSftp;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.support.task.BlockingTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SftpChangedExclusiveReadLockStrategy implements GenericFileExclusiveReadLockStrategy<ChannelSftp.LsEntry> {
    private static final Logger LOG = LoggerFactory.getLogger(SftpChangedExclusiveReadLockStrategy.class);
    private long timeout;
    private long checkInterval = 5000;
    private LoggingLevel readLockLoggingLevel = LoggingLevel.WARN;
    private long minLength = 1;
    private long minAge;
    private boolean fastExistsCheck;

    @Override
    public void prepareOnStartup(
            GenericFileOperations<ChannelSftp.LsEntry> tGenericFileOperations,
            GenericFileEndpoint<ChannelSftp.LsEntry> tGenericFileEndpoint)
            throws Exception {
        // noop
    }

    @Override
    public boolean acquireExclusiveReadLock(
            GenericFileOperations<ChannelSftp.LsEntry> operations, GenericFile<ChannelSftp.LsEntry> file, Exchange exchange)
            throws Exception {
        LOG.trace("Waiting for exclusive read lock to file: {}", file);

        var budgetBuilder = Budgets.iterationTimeBudget()
                .withInterval(Duration.ofMillis(checkInterval));
        if (timeout > 0) {
            budgetBuilder.withMaxDuration(Duration.ofMillis(timeout));
        } else {
            budgetBuilder.withUnlimitedDuration();
        }
        BlockingTask task = Tasks.foregroundTask()
                .withBudget(budgetBuilder.build())
                .withName("sftp-acquire-exclusive-read-lock")
                .build();

        SftpExclusiveReadLockCheck exclusiveReadLockCheck
                = new SftpExclusiveReadLockCheck(fastExistsCheck, minAge, minLength);

        if (!task.run(exchange.getContext(), () -> exclusiveReadLockCheck.tryAcquireExclusiveReadLock(operations, file))) {
            CamelLogger.log(LOG, readLockLoggingLevel,
                    "Cannot acquire read lock within " + timeout + " millis. Will skip the file: " + file);

            return false;
        }

        return true;
    }

    @Override
    public void releaseExclusiveReadLockOnAbort(
            GenericFileOperations<ChannelSftp.LsEntry> operations, GenericFile<ChannelSftp.LsEntry> file, Exchange exchange)
            throws Exception {
        // noop
    }

    @Override
    public void releaseExclusiveReadLockOnRollback(
            GenericFileOperations<ChannelSftp.LsEntry> operations, GenericFile<ChannelSftp.LsEntry> file, Exchange exchange)
            throws Exception {
        // noop
    }

    @Override
    public void releaseExclusiveReadLockOnCommit(
            GenericFileOperations<ChannelSftp.LsEntry> operations, GenericFile<ChannelSftp.LsEntry> file, Exchange exchange)
            throws Exception {
        // noop
    }

    public long getTimeout() {
        return timeout;
    }

    @Override
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getCheckInterval() {
        return checkInterval;
    }

    @Override
    public void setCheckInterval(long checkInterval) {
        this.checkInterval = checkInterval;
    }

    @Override
    public void setReadLockLoggingLevel(LoggingLevel readLockLoggingLevel) {
        this.readLockLoggingLevel = readLockLoggingLevel;
    }

    public long getMinLength() {
        return minLength;
    }

    public void setMinLength(long minLength) {
        this.minLength = minLength;
    }

    public long getMinAge() {
        return minAge;
    }

    public void setMinAge(long minAge) {
        this.minAge = minAge;
    }

    public boolean isFastExistsCheck() {
        return fastExistsCheck;
    }

    public void setFastExistsCheck(boolean fastExistsCheck) {
        this.fastExistsCheck = fastExistsCheck;
    }

    @Override
    public void setMarkerFiler(boolean markerFiler) {
        // noop - not supported by ftp
    }

    @Override
    public void setDeleteOrphanLockFiles(boolean deleteOrphanLockFiles) {
        // noop - not supported by ftp
    }
}
