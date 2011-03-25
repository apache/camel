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

import org.apache.camel.AsyncCallback;
import org.apache.camel.BatchConsumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for remote file consumers.
 */
public abstract class GenericFileConsumer<T> extends ScheduledPollConsumer implements BatchConsumer, ShutdownAware {
    protected final transient Logger log = LoggerFactory.getLogger(getClass());
    protected GenericFileEndpoint<T> endpoint;
    protected GenericFileOperations<T> operations;
    protected boolean loggedIn;
    protected String fileExpressionResult;
    protected int maxMessagesPerPoll;
    protected volatile ShutdownRunningTask shutdownRunningTask;
    protected volatile int pendingExchanges;

    public GenericFileConsumer(GenericFileEndpoint<T> endpoint, Processor processor, GenericFileOperations<T> operations) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.operations = operations;
    }

    /**
     * Poll for files
     */
    protected int poll() throws Exception {
        // must reset for each poll
        fileExpressionResult = null;
        shutdownRunningTask = null;
        pendingExchanges = 0;

        // before we poll is there anything we need to check?
        // such as are we connected to the FTP Server still?
        if (!prePollCheck()) {
            if (log.isDebugEnabled()) {
                log.debug("Skipping poll as pre poll check returned false");
            }
            return 0;
        }

        // gather list of files to process
        List<GenericFile<T>> files = new ArrayList<GenericFile<T>>();
        String name = endpoint.getConfiguration().getDirectory();

        // time how long time it takes to poll
        StopWatch stop = new StopWatch();
        boolean limitHit = !pollDirectory(name, files);
        long delta = stop.stop();
        if (log.isDebugEnabled()) {
            log.debug("Took " + TimeUtils.printDuration(delta) + " to poll: " + name);
        }

        // log if we hit the limit
        if (limitHit) {
            if (log.isDebugEnabled()) {
                log.debug("Limiting maximum messages to poll at " + maxMessagesPerPoll + " files as there was more messages in this poll.");
            }
        }

        // sort files using file comparator if provided
        if (endpoint.getSorter() != null) {
            Collections.sort(files, endpoint.getSorter());
        }

        // sort using build in sorters so we can use expressions
        LinkedList<Exchange> exchanges = new LinkedList<Exchange>();
        for (GenericFile<T> file : files) {
            Exchange exchange = endpoint.createExchange(file);
            endpoint.configureExchange(exchange);
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

        Queue<Exchange> q = exchanges;
        int polledMessages = processBatch(CastUtils.cast(q));

        postPollCheck();

        return polledMessages;
    }

    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    @SuppressWarnings("unchecked")
    public int processBatch(Queue<Object> exchanges) {
        int total = exchanges.size();

        // limit if needed
        if (maxMessagesPerPoll > 0 && total > maxMessagesPerPoll) {
            if (log.isDebugEnabled()) {
                log.debug("Limiting to maximum messages to poll " + maxMessagesPerPoll + " as there was " + total + " messages in this poll.");
            }
            total = maxMessagesPerPoll;
        }

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            // use poll to remove the head so it does not consume memory even after we have processed it
            Exchange exchange = (Exchange) exchanges.poll();
            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // process the current exchange
            processExchange(exchange);
        }
        
        // remove the file from the in progress list in case the batch was limited by max messages per poll
        while (exchanges.size() > 0) {
            Exchange exchange = (Exchange) exchanges.poll();
            GenericFile<T> file = (GenericFile<T>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
            String key = file.getAbsoluteFilePath();
            endpoint.getInProgressRepository().remove(key);
        }

        return total;
    }

    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // store a reference what to do in case when shutting down and we have pending messages
        this.shutdownRunningTask = shutdownRunningTask;
        // do not defer shutdown
        return false;
    }

    public int getPendingExchangesSize() {
        // only return the real pending size in case we are configured to complete all tasks
        if (ShutdownRunningTask.CompleteAllTasks == shutdownRunningTask) {
            return pendingExchanges;
        } else {
            return 0;
        }
    }

    public void prepareShutdown() {
        // noop
    }

    public boolean isBatchAllowed() {
        // stop if we are not running
        boolean answer = isRunAllowed();
        if (!answer) {
            return false;
        }

        if (shutdownRunningTask == null) {
            // we are not shutting down so continue to run
            return true;
        }

        // we are shutting down so only continue if we are configured to complete all tasks
        return ShutdownRunningTask.CompleteAllTasks == shutdownRunningTask;
    }

    /**
     * Whether or not we can continue polling for more files
     *
     * @param fileList  the current list of gathered files
     * @return <tt>true</tt> to continue, <tt>false</tt> to stop due hitting maxMessagesPerPoll limit
     */
    public boolean canPollMoreFiles(List fileList) {
        if (maxMessagesPerPoll <= 0) {
            // no limitation
            return true;
        }

        // then only poll if we haven't reached the max limit
        return fileList.size() < maxMessagesPerPoll;
    }

    /**
     * Override if required. Perform some checks (and perhaps actions) before we poll.
     *
     * @return <tt>true</tt> to poll, <tt>false</tt> to skip this poll.
     */
    protected boolean prePollCheck() throws Exception {
        return true;
    }

    /**
     * Override if required. Perform some checks (and perhaps actions) after we have polled.
     */
    protected void postPollCheck() {
        // noop
    }

    /**
     * Polls the given directory for files to process
     *
     * @param fileName current directory or file
     * @param fileList current list of files gathered
     * @return whether or not to continue polling, <tt>false</tt> means the maxMessagesPerPoll limit has been hit
     */
    protected abstract boolean pollDirectory(String fileName, List<GenericFile<T>> fileList);

    /**
     * Sets the operations to be used.
     * <p/>
     * Can be used to set a fresh operations in case of recovery attempts
     *
     * @param operations the operations
     */
    public void setOperations(GenericFileOperations<T> operations) {
        this.operations = operations;
    }

    /**
     * Processes the exchange
     *
     * @param exchange the exchange
     */
    protected void processExchange(final Exchange exchange) {
        GenericFile<T> file = getExchangeFileProperty(exchange);
        log.trace("Processing file: {}", file);

        // must extract the absolute name before the begin strategy as the file could potentially be pre moved
        // and then the file name would be changed
        String absoluteFileName = file.getAbsoluteFilePath();

        // check if we can begin processing the file
        try {
            final GenericFileProcessStrategy<T> processStrategy = endpoint.getGenericFileProcessStrategy();

            boolean begin = processStrategy.begin(operations, endpoint, exchange, file);
            if (!begin) {
                if (log.isDebugEnabled()) {
                    log.debug(endpoint + " cannot begin processing file: " + file);
                }
                // begin returned false, so remove file from the in progress list as its no longer in progress
                endpoint.getInProgressRepository().remove(absoluteFileName);
                return;
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(endpoint + " cannot begin processing file: " + file + " due to: " + e.getMessage(), e);
            }
            endpoint.getInProgressRepository().remove(absoluteFileName);
            return;
        }

        // must use file from exchange as it can be updated due the
        // preMoveNamePrefix/preMoveNamePostfix options
        final GenericFile<T> target = getExchangeFileProperty(exchange);
        // must use full name when downloading so we have the correct path
        final String name = target.getAbsoluteFilePath();
        try {
            // retrieve the file using the stream
            log.trace("Retrieving file: {} from: {}", name, endpoint);

            // retrieve the file and check it was a success
            boolean retrieved = operations.retrieveFile(name, exchange);
            if (!retrieved) {
                // throw exception to handle the problem with retrieving the file
                // then if the method return false or throws an exception is handled the same in here
                // as in both cases an exception is being thrown
                throw new GenericFileOperationFailedException("Cannot retrieve file: " + file + " from: " + endpoint);
            }

            log.trace("Retrieved file: {} from: {}", name, endpoint);

            // register on completion callback that does the completion strategies
            // (for instance to move the file after we have processed it)
            exchange.addOnCompletion(new GenericFileOnCompletion<T>(endpoint, operations, target, absoluteFileName));

            if (log.isDebugEnabled()) {
                log.debug("About to process file: " + target + " using exchange: " + exchange);
            }

            // process the exchange using the async consumer to support async routing engine
            // which can be supported by this file consumer as all the done work is
            // provided in the GenericFileOnCompletion
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                public void done(boolean doneSync) {
                    // noop
                    log.trace("Done processing file: {} {}", target, doneSync ? "synchronously" : "asynchronously");
                }
            });

        } catch (Exception e) {
            // remove file from the in progress list due to failure
            // (cannot be in finally block due to GenericFileOnCompletion will remove it
            // from in progress when it takes over and processes the file, which may happen
            // by another thread at a later time. So its only safe to remove it if there was an exception)
            endpoint.getInProgressRepository().remove(absoluteFileName);
            handleException(e);
        }
    }

    /**
     * Strategy for validating if the given remote file should be included or not
     *
     * @param file        the file
     * @param isDirectory whether the file is a directory or a file
     * @return <tt>true</tt> to include the file, <tt>false</tt> to skip it
     */
    protected boolean isValidFile(GenericFile<T> file, boolean isDirectory) {
        if (!isMatched(file, isDirectory)) {
            log.trace("File did not match. Will skip this file: {}", file);
            return false;
        } else if (endpoint.isIdempotent() && endpoint.getIdempotentRepository().contains(file.getAbsoluteFilePath())) {
            log.trace("This consumer is idempotent and the file has been consumed before. Will skip this file: {}", file);
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
     * @param file        the file
     * @param isDirectory whether the file is a directory or a file
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
            evaluateFileExpression();
            if (fileExpressionResult != null) {
                if (!name.equals(fileExpressionResult)) {
                    return false;
                }
            }
        }

        // if done file name is enabled, then the file is only valid if a done file exists
        if (endpoint.getDoneFileName() != null) {
            // done file must be in same path as the file
            String doneFileName = endpoint.createDoneFileName(file.getAbsoluteFilePath());
            ObjectHelper.notEmpty(doneFileName, "doneFileName", endpoint);

            // is it a done file name?
            if (endpoint.isDoneFile(file.getFileNameOnly())) {
                log.trace("Skipping done file: {}", file);
                return false;
            }

            // the file is only valid if the done file exist
            if (!operations.existsFile(doneFileName)) {
                log.trace("Done file: {} does not exist", doneFileName);
                return false;
            }
        }

        return true;
    }

    /**
     * Is the given file already in progress.
     *
     * @param file the file
     * @return <tt>true</tt> if the file is already in progress
     */
    protected boolean isInProgress(GenericFile<T> file) {
        String key = file.getAbsoluteFilePath();
        return !endpoint.getInProgressRepository().add(key);
    }

    private void evaluateFileExpression() {
        if (fileExpressionResult == null) {
            // create a dummy exchange as Exchange is needed for expression evaluation
            Exchange dummy = new DefaultExchange(endpoint.getCamelContext());
            fileExpressionResult = endpoint.getFileName().evaluate(dummy, String.class);
        }
    }
    
    @SuppressWarnings("unchecked")
    private GenericFile<T> getExchangeFileProperty(Exchange exchange) {
        return (GenericFile<T>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        
        // prepare on startup
        endpoint.getGenericFileProcessStrategy().prepareOnStartup(operations, endpoint);
    }
}
