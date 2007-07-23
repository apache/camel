/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file.strategy;

import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.component.file.FileExchange;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 * @version $Revision: 1.1 $
 */
public abstract class FileStategySupport implements FileStrategy {
    private static final transient Log log = LogFactory.getLog(FileStategySupport.class);
    private boolean lockFile;

    protected FileStategySupport() {
        this(true);
    }

    protected FileStategySupport(boolean lockFile) {
        this.lockFile = lockFile;
    }

    public boolean begin(FileEndpoint endpoint, FileExchange exchange, File file) throws Exception {
        if (isLockFile()) {
            String lockFileName = file.getAbsoluteFile() + ".lock";
            if (log.isDebugEnabled()) {
                log.debug("Locking the file: " + file + " using the lock file name: " + lockFileName);
            }

            FileChannel channel = new RandomAccessFile(lockFileName, "rw").getChannel();
            FileLock lock = channel.lock();
            if (lock != null) {
                exchange.setProperty("org.apache.camel.fileChannel", channel);
                exchange.setProperty("org.apache.camel.file.lock", lock);
                exchange.setProperty("org.apache.camel.file.lock.name", lockFileName);
                return true;
            }
            return false;
        }
        return true;
    }

    public void commit(FileEndpoint endpoint, FileExchange exchange, File file) throws Exception {
        if (isLockFile()) {
            Channel channel = ExchangeHelper.getMandatoryProperty(exchange, "org.apache.camel.fileChannel", Channel.class);
            String lockfile = ExchangeHelper.getMandatoryProperty(exchange, "org.apache.camel.file.lock.name", String.class);
            if (log.isDebugEnabled()) {
                log.debug("Unlocking file: " + file);
            }
            channel.close();
            File lock = new File(lockfile);
            lock.delete();
        }
    }

    public boolean isLockFile() {
        return lockFile;
    }

    public void setLockFile(boolean lockFile) {
        this.lockFile = lockFile;
    }
}
