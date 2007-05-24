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
package org.apache.camel.component.file;

import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * @version $Revision: 523016 $
 */
public class FileConsumer extends ScheduledPollConsumer<FileExchange> {
    private static final transient Log log = LogFactory.getLog(FileConsumer.class);
    private final FileEndpoint endpoint;
    private boolean recursive = true;
    private boolean attemptFileLock = false;
    private String regexPattern = "";
    private long lastPollTime = 0l;

    public FileConsumer(final FileEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    protected void poll() throws Exception {
        pollFileOrDirectory(endpoint.getFile(), isRecursive());
        lastPollTime = System.currentTimeMillis();
    }

    protected void pollFileOrDirectory(File fileOrDirectory, boolean processDir) {
        if (!fileOrDirectory.isDirectory()) {
            pollFile(fileOrDirectory); // process the file
        }
        else if (processDir) {
            log.debug("Polling directory " + fileOrDirectory);
            File[] files = fileOrDirectory.listFiles();
            for (int i = 0; i < files.length; i++) {
                pollFileOrDirectory(files[i], isRecursive()); // self-recursion
            }
        }
        else {
            log.debug("Skipping directory " + fileOrDirectory);
        }
    }

    protected void pollFile(final File file) {
        if (file.exists() && file.lastModified() > lastPollTime) {
            if (isValidFile(file)) {
                processFile(file);
            }
        }
    }

    protected void processFile(File file) {
        try {
			getProcessor().process(endpoint.createExchange(file));
		} catch (Throwable e) {
			handleException(e);
		}
    }

    protected boolean isValidFile(File file) {
        boolean result = false;
        if (file != null && file.exists()) {
            if (isMatched(file)) {
                if (isAttemptFileLock()) {
                    FileChannel fc = null;
                    try {
                        fc = new RandomAccessFile(file, "rw").getChannel();
                        fc.lock();
                        result = true;
                    }
                    catch (Throwable e) {
                        log.debug("Failed to get the lock on file: " + file, e);
                    }
                    finally {
                        if (fc != null) {
                            try {
                                fc.close();
                            }
                            catch (IOException e) {
                            }
                        }
                    }
                }
                else {
                    result = true;
                }
            }
        }
        return result;
    }

    protected boolean isMatched(File file) {
        boolean result = true;
        if (regexPattern != null && regexPattern.length() > 0) {
            result = file.getName().matches(getRegexPattern());
        }
        return result;
    }

    /**
     * @return the recursive
     */
    public boolean isRecursive() {
        return this.recursive;
    }

    /**
     * @param recursive the recursive to set
     */
    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    /**
     * @return the attemptFileLock
     */
    public boolean isAttemptFileLock() {
        return this.attemptFileLock;
    }

    /**
     * @param attemptFileLock the attemptFileLock to set
     */
    public void setAttemptFileLock(boolean attemptFileLock) {
        this.attemptFileLock = attemptFileLock;
    }

    /**
     * @return the regexPattern
     */
    public String getRegexPattern() {
        return this.regexPattern;
    }

    /**
     * @param regexPattern the regexPattern to set
     */
    public void setRegexPattern(String regexPattern) {
        this.regexPattern = regexPattern;
    }
}
