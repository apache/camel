/**
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
import java.io.RandomAccessFile;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.component.file.FileExchange;
import org.apache.camel.component.file.FileProcessStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Base class for {@link org.apache.camel.component.file.FileProcessStrategy} implementation to extend.
 *
 * @version $Revision$
 */
public abstract class FileProcessStrategySupport implements FileProcessStrategy {
    private static final transient Log LOG = LogFactory.getLog(FileProcessStrategySupport.class);
    private boolean lockFile;
    private FileRenamer lockFileRenamer;

    protected FileProcessStrategySupport() {
        this(true);
    }

    protected FileProcessStrategySupport(boolean lockFile) {
        this(lockFile, new DefaultFileRenamer(null, FileEndpoint.DEFAULT_LOCK_FILE_POSTFIX));
    }

    protected FileProcessStrategySupport(boolean lockFile, FileRenamer lockFileRenamer) {
        this.lockFile = lockFile;
        this.lockFileRenamer = lockFileRenamer;
    }

    public boolean begin(FileEndpoint endpoint, FileExchange exchange, File file) throws Exception {
        if (isLockFile()) {
            File newFile = lockFileRenamer.renameFile(exchange, file);
            String lockFileName = newFile.getAbsolutePath();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Locking the file: " + file + " using the lock file name: " + lockFileName);
            }

            FileChannel channel = new RandomAccessFile(lockFileName, "rw").getChannel();
            FileLock lock = channel.lock();
            if (lock != null) {
                exchange.setProperty("org.apache.camel.file.lock", lock);
                exchange.setProperty("org.apache.camel.file.lock.name", lockFileName);
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    public void commit(FileEndpoint endpoint, FileExchange exchange, File file) throws Exception {
        unlockFile(endpoint, exchange, file);
    }

    public void rollback(FileEndpoint endpoint, FileExchange exchange, File file) {
        try {
            unlockFile(endpoint, exchange, file);
        } catch (Exception e) {
            LOG.warn("Unable to unlock file: " + file, e);
        }
    }

    public boolean isLockFile() {
        return lockFile;
    }

    public void setLockFile(boolean lockFile) {
        this.lockFile = lockFile;
    }

    public FileRenamer getLockFileRenamer() {
        return lockFileRenamer;
    }

    public void setLockFileRenamer(FileRenamer lockFileRenamer) {
        this.lockFileRenamer = lockFileRenamer;
    }

    protected void unlockFile(FileEndpoint endpoint, FileExchange exchange, File file) throws Exception {
        if (isLockFile()) {
            FileLock lock = ExchangeHelper.getMandatoryProperty(exchange, "org.apache.camel.file.lock", FileLock.class);
            String lockFileName = ExchangeHelper.getMandatoryProperty(exchange, "org.apache.camel.file.lock.name", String.class);
            Channel channel = lock.channel();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unlocking file: " + file);
            }
            try {
                lock.release();
            } finally {
                // must close channel
                ObjectHelper.close(channel, "Closing channel", LOG);

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Deleting lock file: " + lockFileName);
                }
                File lockfile = new File(lockFileName);
                lockfile.delete();
            }
        }
    }
}
