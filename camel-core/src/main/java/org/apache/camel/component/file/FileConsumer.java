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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
public class FileConsumer extends ScheduledPollConsumer {
    private static final transient Log LOG = LogFactory.getLog(FileConsumer.class);

    private FileEndpoint endpoint;
    private boolean recursive;
    private String regexPattern = "";
    private boolean exclusiveReadLock = true;

    public FileConsumer(final FileEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    protected synchronized void poll() throws Exception {
        // gather list of files to process
        List<File> files = new ArrayList<File>();
        scanFilesToPoll(endpoint.getFile(), true, files);

        // sort files using file comparator if provided
        if (endpoint.getFileSorter() != null) {
            Collections.sort(files, endpoint.getFileSorter());
        }

        // sort using build in sorters that is expression based
        // first we need to convert to FileExchange objects so we can sort using expressions
        List<FileExchange> exchanges = new ArrayList<FileExchange>(files.size());
        for (File file : files) {
            FileExchange exchange = endpoint.createExchange(file);
            endpoint.configureMessage(file, exchange.getIn());
            exchanges.add(exchange);
        }
        // sort files using exchange comparator if provided
        if (endpoint.getExchangeSorter() != null) {
            Collections.sort(exchanges, endpoint.getExchangeSorter());
        }

        // consume files one by one
        int total = exchanges.size();
        for (int index = 0; index < total; index++) {
            FileExchange exchange = exchanges.get(index);
            // add current index and total as headers
            exchange.getIn().setHeader(FileComponent.HEADER_FILE_BATCH_INDEX, index);
            exchange.getIn().setHeader(FileComponent.HEADER_FILE_BATCH_TOTAL, total);
            processExchange(exchange);
        }
    }

    /**
     * Scans the given file or directory for files to process.
     *
     * @param fileOrDirectory  current file or directory when doing recursion
     * @param processDir  recursive
     * @param fileList  current list of files gathered
     */
    protected void scanFilesToPoll(File fileOrDirectory, boolean processDir, List<File> fileList) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) {
            // not a file so skip it
            return;
        }

        if (!fileOrDirectory.isDirectory()) {
            addFile(fileOrDirectory, fileList);
        } else if (processDir) {
            // directory that can be recursive
            if (LOG.isTraceEnabled()) {
                LOG.trace("Polling directory " + fileOrDirectory);
            }
            File[] files = fileOrDirectory.listFiles();
            for (File file : files) {
                // recursive add the files
                scanFilesToPoll(file, isRecursive(), fileList);
            }
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Skipping directory " + fileOrDirectory);
            }
        }
    }

    /**
     * Processes the given file
     *
     * @param exchange  the file exchange
     */
    protected void processExchange(final FileExchange exchange) {
        final File file = exchange.getFile();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Processing file: " + file);
        }

        try {
            final FileProcessStrategy processStrategy = endpoint.getFileStrategy();

            // is we use excluse read then acquire the exclusive read (waiting until we got it)
            if (exclusiveReadLock) {
                acquireExclusiveReadLock(file);
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
                        }
                    }
                });

            } else {
                LOG.warn(endpoint + " can not process file: " + file);
            }
        } catch (Exception e) {
            handleException(e);
        }
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

    /**
     * Strategy for validating if the given file should be included or not
     * @param file  the file
     * @return true to include the file, false to skip it
     */
    protected boolean validateFile(File file) {
        // NOTE: contains will add if we had a miss
        if (endpoint.isIdempotent() && endpoint.getIdempotentRepository().contains(file.getName())) {
            // skip as we have already processed it
            return false;
        }

        return matchFile(file);
    }

    /**
     * Strategy to perform file matching based on endpoint configuration.
     * <p/>
     * Will always return false for certain files:
     * <ul>
     *    <li>Starting with a dot</li>
     *    <li>lock files</li>
     * </ul>
     *
     * @param file  the file
     * @return true if the file is matche, false if not
     */
    protected boolean matchFile(File file) {
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

        if (endpoint.getFilter() != null) {
            if (!endpoint.getFilter().accept(file)) {
                return false;
            }
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
        if (endpoint.getExcludedNamePostfix() != null) {
            if (name.endsWith(endpoint.getExcludedNamePostfix())) {
                return false;
            }
        }

        return true;
    }

    private void addFile(File file, List<File> fileList) {
        if (validateFile(file)) {
            fileList.add(file);
        }
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

    public boolean isExclusiveReadLock() {
        return exclusiveReadLock;
    }

    public void setExclusiveReadLock(boolean exclusiveReadLock) {
        this.exclusiveReadLock = exclusiveReadLock;
    }

}
