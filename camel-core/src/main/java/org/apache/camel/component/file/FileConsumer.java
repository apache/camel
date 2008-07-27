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
package org.apache.camel.component.file;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.processor.DeadLetterChannel;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * For consuming files.
 *
 * @version $Revision$
 */
public class FileConsumer extends ScheduledPollConsumer<FileExchange> {
    private static final transient Log LOG = LogFactory.getLog(FileConsumer.class);

    private FileEndpoint endpoint;
    private ConcurrentHashMap<File, File> filesBeingProcessed = new ConcurrentHashMap<File, File>();
    private ConcurrentHashMap<File, Long> fileSizes = new ConcurrentHashMap<File, Long>();
    private ConcurrentHashMap<File, Long> noopMap = new ConcurrentHashMap<File, Long>();

    private long lastPollTime;
    private int unchangedDelay;
    private boolean unchangedSize;

    private boolean generateEmptyExchangeWhenIdle;
    private boolean recursive = true;
    private String regexPattern = "";
    private boolean exclusiveRead;

    public FileConsumer(final FileEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    protected synchronized void poll() throws Exception {
        int rc = pollFileOrDirectory(endpoint.getFile(), isRecursive());
        if (rc == 0 && generateEmptyExchangeWhenIdle) {
            final FileExchange exchange = endpoint.createExchange((File)null);
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                public void done(boolean sync) {
                }
            });
        }
        lastPollTime = System.currentTimeMillis();
    }

    /**
     * Pools the given file or directory for files to process.
     *
     * @param fileOrDirectory  file or directory
     * @param processDir  recursive
     * @return the number of files processed or being processed async.
     */
    protected int pollFileOrDirectory(File fileOrDirectory, boolean processDir) {
        if (!fileOrDirectory.isDirectory()) {
            return pollFile(fileOrDirectory); // process the file
        } else if (processDir) {
            int rc = 0;
            if (isValidFile(fileOrDirectory)) {
                LOG.debug("Polling directory " + fileOrDirectory);
                File[] files = fileOrDirectory.listFiles();
                for (File file : files) {
                    rc += pollFileOrDirectory(file, isRecursive()); // self-recursion
                }
            }
            return rc;
        } else {
            LOG.debug("Skipping directory " + fileOrDirectory);
            return 0;
        }
    }

    /**
     * Polls the given file
     *
     * @param file  the file
     * @return returns 1 if the file was processed, 0 otherwise.
     */
    protected int pollFile(final File file) {

        if (!file.exists()) {
            return 0;
        }
        if (!isValidFile(file)) {
            return 0;
        }
        // we only care about file modified times if we are not deleting/moving files
        if (!endpoint.isNoop()) {
            if (filesBeingProcessed.contains(file)) {
                return 1;
            }
            filesBeingProcessed.put(file, file);
        }

        final FileProcessStrategy processStrategy = endpoint.getFileStrategy();
        final FileExchange exchange = endpoint.createExchange(file);

        endpoint.configureMessage(file, exchange.getIn());
        try {
            // is we use excluse read then acquire the exclusive read (waiting until we got it)
            if (exclusiveRead) {
                acquireExclusiveRead(file);
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("About to process file: " + file + " using exchange: " + exchange);
            }
            if (processStrategy.begin(endpoint, exchange, file)) {

                // Use the async processor interface so that processing of
                // the exchange can happen asynchronously
                getAsyncProcessor().process(exchange, new AsyncCallback() {
                    public void done(boolean sync) {
                        boolean failed = exchange.isFailed();
                        boolean handled = DeadLetterChannel.isFailureHandled(exchange);

                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Done processing file: " + file + ". Status is: " + (failed ? "failed: " + failed + ", handled by failure processor: " + handled : "OK"));
                        }

                        if (!failed || handled) {
                            // commit the file strategy if there was no failure or already handled by the DeadLetterChannel
                            processStrategyCommit(processStrategy, exchange, file, handled);
                        } else if (failed && !handled) {
                            // there was an exception but it was not handled by the DeadLetterChannel
                            handleException(exchange.getException());
                        }

                        filesBeingProcessed.remove(file);
                    }
                });

            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(endpoint + " cannot process file: " + file);
                }
            }
        } catch (Throwable e) {
            handleException(e);
        }

        return 1;
    }

    protected void acquireExclusiveRead(File file) throws IOException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Acquiring exclusive read (avoid reading file that is in progress of being written) to " + file);
        }

        // try to acquire rw lock on the file before we can consume it
        FileChannel channel = new RandomAccessFile(file, "rw").getChannel();
        try {
            FileLock lock = channel.lock();
            // just release it now we dont want to hold it during the rest of the processing
            lock.release();
        } finally {
            // must close channel
            ObjectHelper.close(channel, "FileConsumer during acquiring of exclusive read", LOG);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Acquired exclusive read to: " + file);
        }
    }


    /**
     * Strategy when the file was processed and a commit should be executed.
     *
     * @param processStrategy   the strategy to perform the commit
     * @param exchange          the exchange
     * @param file              the file processed
     * @param failureHandled    is <tt>false</tt> if the exchange was processed succesfully, <tt>true</tt> if
     * an exception occured during processing but it was handled by the failure processor (usually the
     * DeadLetterChannel).
     */
    protected void processStrategyCommit(FileProcessStrategy processStrategy, FileExchange exchange,
                                         File file, boolean failureHandled) {
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Committing file strategy: " + processStrategy + " for file: " + file + (failureHandled ? " that was handled by the failure processor." : ""));
            }
            processStrategy.commit(endpoint, exchange, file);
        } catch (Exception e) {
            LOG.warn("Error committing file strategy: " + processStrategy, e);
            handleException(e);
        }
    }

    protected boolean isValidFile(File file) {
        boolean result = false;
        if (file != null && file.exists()) {
            // TODO: maybe use a configurable strategy instead of the hardcoded one based on last file change
            if (isMatched(file) && isChanged(file)) {
                result = true;
            }
        }
        return result;
    }

    protected boolean isChanged(File file) {
        if (file == null) {
            // Sanity check
            return false;
        } else if (file.isDirectory()) {
            // Allow recursive polling to descend into this directory
            return true;
        } else {
            boolean lastModifiedCheck = false;
            long modifiedDuration = 0;
            if (getUnchangedDelay() > 0) {
                modifiedDuration = System.currentTimeMillis() - file.lastModified();
                lastModifiedCheck = modifiedDuration >= getUnchangedDelay();
            }

            long fileModified = file.lastModified();
            Long previousModified = noopMap.get(file);
            noopMap.put(file, fileModified);
            if (previousModified == null || fileModified > previousModified) {
                lastModifiedCheck = true;
            }

            boolean sizeCheck = false;
            long sizeDifference = 0;
            if (isUnchangedSize()) {
                Long value = fileSizes.get(file);
                if (value == null) {
                    sizeCheck = true;
                } else {
                    sizeCheck = file.length() != value;
                }
            }

            boolean answer = lastModifiedCheck || sizeCheck;

            if (LOG.isDebugEnabled()) {
                LOG.debug("file:" + file + " isChanged:" + answer + " " + "sizeCheck:" + sizeCheck + "("
                          + sizeDifference + ") " + "lastModifiedCheck:" + lastModifiedCheck + "("
                          + modifiedDuration + ")");
            }

            if (isUnchangedSize()) {
                if (answer) {
                    fileSizes.put(file, file.length());
                } else {
                    fileSizes.remove(file);
                }
            }

            return answer;
        }
    }

    protected boolean isMatched(File file) {
        String name = file.getName();
        if (regexPattern != null && regexPattern.length() > 0) {
            if (!name.matches(getRegexPattern())) {
                return false;
            }
        }
        String[] prefixes = endpoint.getExcludedNamePrefixes();
        if (prefixes != null) {
            for (String prefix : prefixes) {
                if (name.startsWith(prefix)) {
                    return false;
                }
            }
        }
        String[] postfixes = endpoint.getExcludedNamePostfixes();
        if (postfixes != null) {
            for (String postfix : postfixes) {
                if (name.endsWith(postfix)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isRecursive() {
        return this.recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public String getRegexPattern() {
        return this.regexPattern;
    }

    public void setRegexPattern(String regexPattern) {
        this.regexPattern = regexPattern;
    }

    public boolean isGenerateEmptyExchangeWhenIdle() {
        return generateEmptyExchangeWhenIdle;
    }

    public void setGenerateEmptyExchangeWhenIdle(boolean generateEmptyExchangeWhenIdle) {
        this.generateEmptyExchangeWhenIdle = generateEmptyExchangeWhenIdle;
    }

    public int getUnchangedDelay() {
        return unchangedDelay;
    }

    public void setUnchangedDelay(int unchangedDelay) {
        this.unchangedDelay = unchangedDelay;
    }

    public boolean isUnchangedSize() {
        return unchangedSize;
    }

    public void setUnchangedSize(boolean unchangedSize) {
        this.unchangedSize = unchangedSize;
    }

    public boolean isExclusiveRead() {
        return exclusiveRead;
    }

    public void setExclusiveRead(boolean exclusiveRead) {
        this.exclusiveRead = exclusiveRead;
    }
}
