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
 * Base class for remote file consumers.
 */
public abstract class GenericFileConsumer<T> extends ScheduledPollConsumer {
    protected final transient Log log = LogFactory.getLog(getClass());
    protected GenericFileEndpoint<T> endpoint;
    protected GenericFileOperations<T> operations;
    protected boolean loggedIn;

    public GenericFileConsumer(GenericFileEndpoint<T> endpoint, Processor processor, GenericFileOperations<T> operations) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.operations = operations;
    }

    /**
     * Poll for files
     */
    protected void poll() throws Exception {

        // before we poll is there anything we need to check ? Such as are we
        // connected to the FTP Server Still ?
        if (!prePollCheck()) {
            log.debug("Skipping pool as pre poll check returned false");
        }

        // gather list of files to process
        List<GenericFile<T>> files = new ArrayList<GenericFile<T>>();

        String name = endpoint.getConfiguration().getFile();        
        boolean isDirectory = endpoint.isDirectory();
        if (isDirectory) {
            pollDirectory(name, files);
        } else {
            pollFile(name, files);
        }

        // sort files using file comparator if provided
        if (endpoint.getSorter() != null) {
            Collections.sort(files, endpoint.getSorter());
        }

        // sort using build in sorters that is expression based
        // first we need to convert to RemoteFileExchange objects so we can sort
        // using expressions
        List<GenericFileExchange<T>> exchanges = new ArrayList<GenericFileExchange<T>>(files.size());
        for (GenericFile<T> file : files) {
            GenericFileExchange<T> exchange = endpoint.createExchange(file);
            endpoint.configureMessage(file, exchange.getIn());
            exchanges.add(exchange);
        }
        // sort files using exchange comparator if provided
        if (endpoint.getSortBy() != null) {
            Collections.sort(exchanges, endpoint.getSortBy());
        }

        // consume files one by one
        int total = exchanges.size();
        if (total > 0 && log.isDebugEnabled()) {
            log.debug("Total " + total + " files to consume");
        }
        for (int index = 0; index < total; index++) {
            GenericFileExchange<T> exchange = exchanges.get(index);
            // add current index and total as headers
            exchange.getIn().setHeader(FileComponent.HEADER_FILE_BATCH_INDEX, index);
            exchange.getIn().setHeader(FileComponent.HEADER_FILE_BATCH_TOTAL, total);
            exchange.getIn().setHeader(NewFileComponent.HEADER_FILE_BATCH_INDEX, index);
            exchange.getIn().setHeader(NewFileComponent.HEADER_FILE_BATCH_TOTAL, total);
            processExchange(exchange);
        }
    }

    /**
     * Override if required. Perform some checks (and perhaps actions) before we
     * poll.
     *
     * @return true to poll, false to skip this poll.
     */
    protected boolean prePollCheck() throws Exception {
        return true;
    }

    /**
     * Polls the given directory for files to process
     *
     * @param fileName current directory or file
     * @param fileList current list of files gathered
     */
    protected abstract void pollDirectory(String fileName, List<GenericFile<T>> fileList);

    /**
     * Polls the given file
     *
     * @param fileName the file name
     * @param fileList current list of files gathered
     */
    protected abstract void pollFile(String fileName, List<GenericFile<T>> fileList);

    /**
     * Processes the exchange
     *
     * @param exchange the exchange
     */
    protected void processExchange(final GenericFileExchange<T> exchange) {
        if (log.isTraceEnabled()) {
            log.trace("Processing remote file: " + exchange.getGenericFile());
        }

        try {
            final GenericFileProcessStrategy<T> processStrategy = endpoint.getGenericFileProcessStrategy();

            if (processStrategy.begin(operations, endpoint, exchange, exchange.getGenericFile())) {

                // must use file from exchange as it can be updated due the
                // preMoveNamePrefix/preMoveNamePostfix options
                final GenericFile<T> target = exchange.getGenericFile();
                // must use full name when downloading so we have the correct path
                final String name = target.getAbsoluteFileName();

                // retrieve the file using the stream
                if (log.isTraceEnabled()) {
                    log.trace("Retreiving file: " + name + " from: " + endpoint);
                }

                operations.retrieveFile(name, exchange);

                if (log.isTraceEnabled()) {
                    log.trace("Retrieved file: " + name + " from: " + endpoint);
                }

                if (log.isDebugEnabled()) {
                    log.debug("About to process file: " + target + " using exchange: " + exchange);
                }
                // Use the async processor interface so that processing of
                // the exchange can happen asynchronously
                getAsyncProcessor().process(exchange, new AsyncCallback() {
                    public void done(boolean sync) {
                        final GenericFile<T> file = exchange.getGenericFile();
                        boolean failed = exchange.isFailed();
                        boolean handled = DeadLetterChannel.isFailureHandled(exchange);

                        if (log.isDebugEnabled()) {
                            log.debug("Done processing file: " + file + ". Status is: "
                                    + (failed ? "failed: " + failed + ", handled by failure processor: " + handled : "processed OK"));
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
                log.warn(endpoint + " cannot process remote file: " + exchange.getGenericFile());
            }
        } catch (Exception e) {
            handleException(e);
        }

    }

    /**
     * Strategy when the file was processed and a commit should be executed.
     *
     * @param processStrategy the strategy to perform the commit
     * @param exchange        the exchange
     * @param file            the file processed
     * @param failureHandled  is <tt>false</tt> if the exchange was processed succesfully,
     *                        <tt>true</tt> if an exception occured during processing but it
     *                        was handled by the failure processor (usually the DeadLetterChannel).
     */
    @SuppressWarnings("unchecked")
    protected void processStrategyCommit(GenericFileProcessStrategy<T> processStrategy,
                                         GenericFileExchange<T> exchange, GenericFile<T> file, boolean failureHandled) {
        if (endpoint.isIdempotent()) {
            // only add to idempotent repository if we could process the file
            // only use the filename as the key as the file could be moved into a done folder
            endpoint.getIdempotentRepository().add(file.getFileName());
        }

        try {
            if (log.isDebugEnabled()) {
                log.debug("Committing remote file strategy: " + processStrategy + " for file: " + file
                        + (failureHandled ? " that was handled by the failure processor." : ""));
            }
            processStrategy.commit(operations, endpoint, exchange, file);
        } catch (Exception e) {
            log.warn("Error committing remote file strategy: " + processStrategy, e);
            handleException(e);
        }
    }

    /**
     * Strategy when the file was not processed and a rollback should be
     * executed.
     *
     * @param processStrategy the strategy to perform the commit
     * @param exchange        the exchange
     * @param file            the file processed
     */
    protected void processStrategyRollback(GenericFileProcessStrategy<T> processStrategy,
                                           GenericFileExchange<T> exchange, GenericFile<T> file) {
        if (log.isDebugEnabled()) {
            log.debug("Rolling back remote file strategy: " + processStrategy + " for file: " + file);
        }
        try {
            processStrategy.rollback(operations, endpoint, exchange, file);
        } catch (Exception e) {
            log.warn("Error rolling back remote file strategy: " + processStrategy, e);
            handleException(e);
        }
    }

    /**
     * Strategy for validating if the given remote file should be included or
     * not
     *
     * @param file        the remote file
     * @param isDirectory wether the file is a directory or a file
     * @return <tt>true</tt> to include the file, <tt>false</tt> to skip it
     */
    @SuppressWarnings("unchecked")
    protected boolean isValidFile(GenericFile<T> file, boolean isDirectory) {
        if (!isMatched(file, isDirectory)) {
            if (log.isTraceEnabled()) {
                log.trace("Remote file did not match. Will skip this remote file: " + file);
            }
            return false;
        } else if (endpoint.isIdempotent() && endpoint.getIdempotentRepository().contains(file.getFileName())) {
            // only use the filename as the key as the file could be moved into a done folder
            if (log.isTraceEnabled()) {
                log.trace("RemoteFileConsumer is idempotent and the file has been consumed before. Will skip this remote file: " + file);
            }
            return false;
        }

        // file matched
        return true;
    }

    /**
     * Strategy to perform file matching based on endpoint configuration.
     * <p/>
     * Will always return <tt>false</tt> for certain files/folders:
     * <ul>
     * <li>Starting with a dot</li>
     * <li>lock files</li>
     * </ul>
     * And then <tt>true</tt> for directories.
     *
     * @param file        the remote file
     * @param isDirectory wether the file is a directory or a file
     * @return <tt>true</tt> if the remote file is matched, <tt>false</tt> if
     *         not
     */
    protected boolean isMatched(GenericFile<T> file, boolean isDirectory) {
        String name = file.getFileName();

        // folders/names starting with dot is always skipped (eg. ".", ".camel", ".camelLock")
        if (name.startsWith(".")) {
            return false;
        }

        // lock files should be skipped
        if (name.endsWith(NewFileComponent.DEFAULT_LOCK_FILE_POSTFIX)) {
            return false;
        }

        // directories so far is always regarded as matched (matching on the name is only for files)
        if (isDirectory) {
            return true;
        }

        if (endpoint.getFilter() != null) {
            if (!endpoint.getFilter().accept(file)) {
                return false;
            }
        }

        if (ObjectHelper.isNotEmpty(endpoint.getRegexPattern())) {
            if (!name.matches(endpoint.getRegexPattern())) {
                return false;
            }
        }

        if (ObjectHelper.isNotEmpty(endpoint.getExcludedNamePrefix())) {
            if (name.startsWith(endpoint.getExcludedNamePrefix())) {
                return false;
            }
        }
        if (ObjectHelper.isNotEmpty(endpoint.getExcludedNamePostfix())) {
            if (name.endsWith(endpoint.getExcludedNamePostfix())) {
                return false;
            }
        }

        return true;
    }

}
