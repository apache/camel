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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.camel.BatchConsumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Base class for remote file consumers.
 */
public abstract class GenericFileConsumer<T> extends ScheduledPollConsumer implements BatchConsumer {
    protected final transient Log log = LogFactory.getLog(getClass());
    protected GenericFileEndpoint<T> endpoint;
    protected GenericFileOperations<T> operations;
    protected boolean loggedIn;
    protected String fileExpressionResult;
    protected int maxMessagesPerPoll;

    public GenericFileConsumer(GenericFileEndpoint<T> endpoint, Processor processor, GenericFileOperations<T> operations) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.operations = operations;
    }

    /**
     * Poll for files
     */
    protected void poll() throws Exception {
        // must reset for each poll
        fileExpressionResult = null;

        // before we poll is there anything we need to check ? Such as are we
        // connected to the FTP Server Still ?
        if (!prePollCheck()) {
            log.debug("Skipping pool as pre poll check returned false");
        }

        // gather list of files to process
        List<GenericFile<T>> files = new ArrayList<GenericFile<T>>();

        String name = endpoint.getConfiguration().getDirectory();
        pollDirectory(name, files);

        // sort files using file comparator if provided
        if (endpoint.getSorter() != null) {
            Collections.sort(files, endpoint.getSorter());
        }

        // sort using build in sorters that is expression based
        // first we need to convert to RemoteFileExchange objects so we can sort
        // using expressions
        LinkedList<GenericFileExchange> exchanges = new LinkedList<GenericFileExchange>();
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

        processBatch(exchanges);
    }

    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    @SuppressWarnings("unchecked")
    public void processBatch(Queue exchanges) {
        int total = exchanges.size();

        // limit if needed
        if (maxMessagesPerPoll > 0 && total > maxMessagesPerPoll) {
            log.debug("Limiting to maximum messages to poll " + maxMessagesPerPoll + " as there was " + total + " messages in this poll.");
            total = maxMessagesPerPoll;
        }

        for (int index = 0; index < total && isRunAllowed(); index++) {
            // only loop if we are started (allowed to run)
            // use poll to remove the head so it does not consume memory even after we have processed it
            GenericFileExchange<T> exchange = (GenericFileExchange<T>) exchanges.poll();
            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // process the current exchange
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

            boolean begin = processStrategy.begin(operations, endpoint, exchange, exchange.getGenericFile());
            if (!begin) {
                log.warn(endpoint + " cannot begin processing file: " + exchange.getGenericFile());
                return;
            }

            // must use file from exchange as it can be updated due the
            // preMoveNamePrefix/preMoveNamePostfix options
            final GenericFile<T> target = exchange.getGenericFile();
            // must use full name when downloading so we have the correct path
            final String name = target.getAbsoluteFilePath();

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

            // register on completion callback that does the completiom stategies
            // (for instance to move the file after we have processed it)
            exchange.addOnCompletion(new GenericFileOnCompletion<T>(endpoint, operations));

            // process the exchange
            getProcessor().process(exchange);

        } catch (Exception e) {
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
                log.trace("File did not match. Will skip this file: " + file);
            }
            return false;
        } else if (endpoint.isIdempotent() && endpoint.getIdempotentRepository().contains(file.getFileName())) {
            // only use the filename as the key as the file could be moved into a done folder
            if (log.isTraceEnabled()) {
                log.trace("This consumer is idempotent and the file has been consumed before. Will skip this file: " + file);
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
     * @return <tt>true</tt> if the remote file is matched, <tt>false</tt> if not
     */
    protected boolean isMatched(GenericFile<T> file, boolean isDirectory) {
        String name = file.getFileNameOnly();

        // folders/names starting with dot is always skipped (eg. ".", ".camel", ".camelLock")
        if (name.startsWith(".")) {
            return false;
        }

        // lock files should be skipped
        if (name.endsWith(FileComponent.DEFAULT_LOCK_FILE_POSTFIX)) {
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

        if (ObjectHelper.isNotEmpty(endpoint.getExclude())) {
            if (name.matches(endpoint.getExclude())) {
                return false;
            }
        }

        if (ObjectHelper.isNotEmpty(endpoint.getInclude())) {
            if (!name.matches(endpoint.getInclude())) {
                return false;
            }
        }

        // use file expression for a simple dynamic file filter
        if (endpoint.getFileName() != null) {
            evaluteFileExpression();
            if (fileExpressionResult != null) {
                if (!name.equals(fileExpressionResult)) {
                    return false;
                }
            }
        }

        return true;
    }

    private void evaluteFileExpression() {
        if (fileExpressionResult == null) {
            // create a dummy exchange as Exchange is needed for expression evaluation
            Exchange dummy = new DefaultExchange(endpoint.getCamelContext());
            fileExpressionResult = endpoint.getFileName().evaluate(dummy, String.class);
        }
    }


}
