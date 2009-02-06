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
 * @deprecated will be replaced with NewFile in Camel 2.0
 */
public class FileConsumer extends ScheduledPollConsumer {
    private static final transient Log LOG = LogFactory.getLog(FileConsumer.class);

    private FileEndpoint endpoint;

    public FileConsumer(final FileEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    protected void poll() throws Exception {
        // gather list of files to process
        List<File> files = new ArrayList<File>();

        boolean isDirectory = endpoint.getFile().isDirectory();
        if (isDirectory) {
            pollDirectory(endpoint.getFile(), files);
        } else {
            pollFile(endpoint.getFile(), files);
        }

        // sort files using file comparator if provided
        if (endpoint.getSorter() != null) {
            Collections.sort(files, endpoint.getSorter());
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
        if (endpoint.getSortBy() != null) {
            Collections.sort(exchanges, endpoint.getSortBy());
        }

        // consume files one by one
        int total = exchanges.size();
        if (total > 0 && LOG.isDebugEnabled()) {
            LOG.debug("Total " + total + " files to consume");
        }
        for (int index = 0; index < total; index++) {
            FileExchange exchange = exchanges.get(index);
            // add current index and total as headers
            exchange.getIn().setHeader(FileComponent.HEADER_FILE_BATCH_INDEX, index);
            exchange.getIn().setHeader(FileComponent.HEADER_FILE_BATCH_TOTAL, total);
            processExchange(exchange);
        }
    }

    /**
     * Polls the given directory for files to process
     *
     * @param fileOrDirectory current directory or file
     * @param fileList        current list of files gathered
     */
    protected void pollDirectory(File fileOrDirectory, List<File> fileList) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) {
            return;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Polling directory: " + fileOrDirectory.getPath());
        }
        File[] files = fileOrDirectory.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                if (endpoint.isRecursive() && isValidFile(file)) {
                    // recursive scan and add the sub files and folders
                    pollDirectory(file, fileList);
                }
            } else if (file.isFile()) {
                if (isValidFile(file)) {
                    // matched file so add
                    fileList.add(file);
                }
            } else {
                LOG.debug("Ignoring unsupported file type " + file);
            }
        }
    }

    /**
     * Polls the given file
     *
     * @param file     the file
     * @param fileList current list of files gathered
     */
    protected void pollFile(File file, List<File> fileList) {
        if (file == null || !file.exists()) {
            return;
        }

        if (isValidFile(file)) {
            // matched file so add
            fileList.add(file);
        }
    }

    /**
     * Processes the given file
     *
     * @param exchange the file exchange
     */
    protected void processExchange(final FileExchange exchange) {
        final File target = exchange.getFile();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Processing file: " + target);
        }

        try {
            final FileProcessStrategy processStrategy = endpoint.getFileStrategy();

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
                        }
                    }
                });

            } else {
                LOG.warn(endpoint + " cannot process file: " + target);
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
     * @param failureHandled  is <tt>false</tt> if the exchange was processed succesfully, <tt>true</tt> if
     *                        an exception occured during processing but it was handled by the failure processor (usually the
     *                        DeadLetterChannel).
     */
    protected void processStrategyCommit(FileProcessStrategy processStrategy, FileExchange exchange,
                                         File file, boolean failureHandled) {
        if (endpoint.isIdempotent()) {
            // only add to idempotent repository if we could process the file
            endpoint.getIdempotentRepository().add(file.getName());
        }

        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Committing file strategy: " + processStrategy + " for file: "
                        + file + (failureHandled ? " that was handled by the failure processor." : ""));
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
     * @param processStrategy the strategy to perform the commit
     * @param exchange        the exchange
     * @param file            the file processed
     */
    protected void processStrategyRollback(FileProcessStrategy processStrategy, FileExchange exchange, File file) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Rolling back file strategy: " + processStrategy + " for file: " + file);
        }
        processStrategy.rollback(endpoint, exchange, file);
    }

    /**
     * Strategy for validating if the given file should be included or not
     *
     * @param file the file
     * @return true to include the file, false to skip it
     */
    protected boolean isValidFile(File file) {
        if (!isMatched(file)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("File did not match. Will skip this file: " + file);
            }
            return false;
        } else if (endpoint.isIdempotent() && endpoint.getIdempotentRepository().contains(file.getName())) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("FileConsumer is idempotent and the file has been consumed before. Will skip this file: " + file);
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
     *   <li>Starting with a dot</li>
     *   <li>lock files</li>
     * </ul>
     * And then <tt>true</tt> for directories.
     *
     * @param file the file
     * @return true if the file is matched, false if not
     */
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

        if (ObjectHelper.isNotEmpty(endpoint.getExcludeNamePrefix())) {
            if (name.startsWith(endpoint.getExcludeNamePrefix())) {
                return false;
            }
        }
        if (ObjectHelper.isNotEmpty(endpoint.getExcludeNamePostfix())) {
            if (name.endsWith(endpoint.getExcludeNamePostfix())) {
                return false;
            }
        }

        return true;
    }

}
