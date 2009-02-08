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

import org.apache.camel.Exchange;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileExclusiveReadLockStrategy;
import org.apache.camel.component.file.GenericFileOperations;
import org.apache.camel.component.file.NewFileComponent;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Acquires read lock to the given file using a marker file so other Camel consumers wont acquire the same file.
 * This is the default behaviour in Camel 1.x.
 */
public class NewMarkerFileExclusiveReadLockStrategy implements GenericFileExclusiveReadLockStrategy<File> {
    private static final transient Log LOG = LogFactory.getLog(NewMarkerFileExclusiveReadLockStrategy.class);

    @SuppressWarnings("unchecked")
    public boolean acquireExclusiveReadLock(GenericFileOperations<File> fileGenericFileOperations,
                                            GenericFile<File> file, Exchange exchange) throws Exception {

        String lockFileName = file.getAbsoluteFileName() + NewFileComponent.DEFAULT_LOCK_FILE_POSTFIX;
        if (LOG.isTraceEnabled()) {
            LOG.trace("Locking the file: " + file + " using the lock file name: " + lockFileName);
        }

        FileChannel channel = new RandomAccessFile(lockFileName, "rw").getChannel();
        FileLock lock = channel.lock();
        if (lock != null) {
            exchange.setProperty("CamelFileLock", lock);
            exchange.setProperty("CamelFileLockName", lockFileName);
            return true;
        } else {
            return false;
        }
    }

    public void releaseExclusiveReadLock(GenericFileOperations<File> fileGenericFileOperations,
                                         GenericFile<File> fileGenericFile, Exchange exchange) throws Exception {
        FileLock lock = ExchangeHelper.getMandatoryProperty(exchange, "CamelFileLock", FileLock.class);
        String lockFileName = ExchangeHelper.getMandatoryProperty(exchange, "CamelFileLockName", String.class);
        Channel channel = lock.channel();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Unlocking file: " + lockFileName);
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

    public void setTimeout(long timeout) {
        // noop
    }

}
