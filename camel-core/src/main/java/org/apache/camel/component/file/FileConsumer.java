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
import org.apache.camel.util.LRUCache;
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
    private ConcurrentHashMap<File, Long> fileSizes = new ConcurrentHashMap<File, Long>(new LRUCache(1000));
    private ConcurrentHashMap<File, Long> noopMap = new ConcurrentHashMap<File, Long>(new LRUCache(1000));

    // the options below is @deprecated and will be removed in Camel 2.0
    private long lastPollTime;
    private int unchangedDelay;
    private boolean unchangedSize;
    private boolean generateEmptyExchangeWhenIdle;
    private boolean alwaysConsume;

    private boolean recursive;
    private String regexPattern = "";
    private boolean exclusiveReadLock = true;

    public FileConsumer(final FileEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    protected synchronized void poll() throws Exception {
        // should be true the first time as its the top directory
        int rc = pollFileOrDirectory(endpoint.getFile(), true);

        // if no files consumes and using generateEmptyExchangeWhenIdle option then process an empty exchange 
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
            // process the file
            return pollFile(fileOrDirectory);
        } else if (processDir) {
            // directory that can be recursive
            int rc = 0;
            if (isValidFile(fileOrDirectory)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Polling directory " + fileOrDirectory);
                }
                File[] files = fileOrDirectory.listFiles();
                for (File file : files) {
                    rc += pollFileOrDirectory(file, isRecursive()); // self-recursion
                }
            }
            return rc;
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Skipping directory " + fileOrDirectory);
            }
            return 0;
        }
    }

    /**
     * Polls the given file
     *
     * @param target  the file
     * @return returns 1 if the file was processed, 0 otherwise.
     */
    protected int pollFile(final File target) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Polling file: " + target);
        }

        if (!target.exists()) {
            return 0;
        }
        if (!isValidFile(target)) {
            return 0;
        }
        // we only care about file modified times if we are not deleting/moving files
        if (!endpoint.isNoop()) {
            if (filesBeingProcessed.contains(target)) {
                return 1;
            }
            filesBeingProcessed.put(target, target);
        }

        final FileProcessStrategy processStrategy = endpoint.getFileStrategy();
        final FileExchange exchange = endpoint.createExchange(target);

        endpoint.configureMessage(target, exchange.getIn());
        try {
            // is we use excluse read then acquire the exclusive read (waiting until we got it)
            if (exclusiveReadLock) {
                acquireExclusiveReadLock(target);
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("About to process file: " + target + " using exchange: " + exchange);
            }
            if (processStrategy.begin(endpoint, exchange, target)) {

                // Use the async processor interface so that processing of
                // the exchange can happen asynchronously
                getAsyncProcessor().process(exchange, new AsyncCallback() {
                    public void done(boolean sync) {
                        // must use file from exchange as it can be updated due the preMoveNamePrefix/preMoveNamePostfix options
                        final File file = exchange.getFile();
                        boolean failed = exchange.isFailed();
                        boolean handled = DeadLetterChannel.isFailureHandled(exchange);

                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Done processing file: " + file + ". Status is: " + (failed ? "failed: " + failed + ", handled by failure processor: " + handled : "processed OK"));
                        }

                        boolean committed = false;
                        try {
                            if (!failed || handled) {
                                // commit the file strategy if there was no failure or already handled by the DeadLetterChannel
                                processStrategyCommit(processStrategy, exchange, file, handled);
                                committed = true;
                            } else {
                                // there was an exception but it was not handled by the DeadLetterChannel
                                handleException(exchange.getException());
                            }
                        } finally {
                            if (!committed) {
                                processStrategyRollback(processStrategy, exchange, file);
                            }
                            filesBeingProcessed.remove(file);
                        }
                    }
                });

            } else {
                LOG.warn(endpoint + " can not process file: " + target);
            }
        } catch (Throwable e) {
            handleException(e);
        }

        return 1;
    }

    /**
     * Acquires exclusive read lock to the given file. Will wait until the lock is granted.
     * After granting the read lock it is realeased, we just want to make sure that when we start
     * consuming the file its not currently in progress of being written by third party.
     */
    protected void acquireExclusiveReadLock(File file) throws IOException {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Waiting for exclusive read lock to file: " + file);
        }

        // try to acquire rw lock on the file before we can consume it
        FileChannel channel = new RandomAccessFile(file, "rw").getChannel();
        try {
            FileLock lock = channel.lock();
            if (LOG.isTraceEnabled()) {
                LOG.trace("Acquired exclusive read lock: " + lock + " to file: " + file);
            }
            // just release it now we dont want to hold it during the rest of the processing
            lock.release();
        } finally {
            // must close channel
            ObjectHelper.close(channel, "FileConsumer during acquiring of exclusive read lock", LOG);
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

    /**
     * Strategy when the file was not processed and a rollback should be executed.
     *
     * @param processStrategy   the strategy to perform the commit
     * @param exchange          the exchange
     * @param file              the file processed
     */
    protected void processStrategyRollback(FileProcessStrategy processStrategy, FileExchange exchange, File file) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Rolling back file strategy: " + processStrategy + " for file: " + file);
        }
        processStrategy.rollback(endpoint, exchange, file);
    }

    protected boolean isValidFile(File file) {
        boolean result = false;
        if (file != null && file.exists()) {
            // TODO: maybe use a configurable strategy instead of the hardcoded one based on last file change
            if (isMatched(file) && (alwaysConsume || isChanged(file))) {
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
            // @deprecated will be removed on Camel 2.0
            // the code below is kinda hard to maintain. We should strive to remove
            // this stuff in Camel 2.0 to keep this component simple and no surprises for end-users
            // this stuff is not persistent so restarting Camel will reset the state
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

        // folders/names starting with dot is always skipped (eg. ".", ".camel", ".camelLock")
        if (name.startsWith(".")) {
            return false;
        }
        // lock files should be skipped
        if (name.endsWith(FileEndpoint.DEFAULT_LOCK_FILE_POSTFIX)) {
            return false;
        }

        // directories so far is always regarded as matched (matching on the name is only for files)
        if (file.isDirectory()) {
            return true;
        }

        if (regexPattern != null && regexPattern.length() > 0) {
            if (!name.matches(regexPattern)) {
                return false;
            }
        }

        if (endpoint.getExcludedNamePrefix() != null) {
            if (name.startsWith(endpoint.getExcludedNamePrefix())) {
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
        if (endpoint.getExcludedNamePostfix() != null) {
            if (name.endsWith(endpoint.getExcludedNamePostfix())) {
                return false;
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

    /**
     * @deprecated will be removed in Camel 2.0
     */
    public void setGenerateEmptyExchangeWhenIdle(boolean generateEmptyExchangeWhenIdle) {
        this.generateEmptyExchangeWhenIdle = generateEmptyExchangeWhenIdle;
    }

    public int getUnchangedDelay() {
        return unchangedDelay;
    }

    /**
     * @deprecated will be removed in Camel 2.0
     */
    public void setUnchangedDelay(int unchangedDelay) {
        this.unchangedDelay = unchangedDelay;
    }

    public boolean isUnchangedSize() {
        return unchangedSize;
    }

    /**
     * @deprecated will be removed in Camel 2.0
     */
    public void setUnchangedSize(boolean unchangedSize) {
        this.unchangedSize = unchangedSize;
    }

    public boolean isExclusiveReadLock() {
        return exclusiveReadLock;
    }

    public void setExclusiveReadLock(boolean exclusiveReadLock) {
        this.exclusiveReadLock = exclusiveReadLock;
    }

    public boolean isAlwaysConsume() {
        return alwaysConsume;
    }

    /**
     * @deprecated will be removed in Camel 2.0 (not needed when we get rid of last polltimestamp)
     */
    public void setAlwaysConsume(boolean alwaysConsume) {
        this.alwaysConsume = alwaysConsume;
    }

    public boolean isTimestamp() {
        return !alwaysConsume;
    }

    /**
     * @deprecated will be removed in Camel 2.0 (not needed when we get rid of last polltimestamp)
     */
    public void setTimestamp(boolean timestamp) {
        this.alwaysConsume = !timestamp;
    }
}
