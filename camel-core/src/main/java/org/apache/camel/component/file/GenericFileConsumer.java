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
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.impl.ScheduledBatchPollingConsumer;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for file consumers.
 */
public abstract class GenericFileConsumer<T> extends ScheduledBatchPollingConsumer {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected GenericFileEndpoint<T> endpoint;
    protected GenericFileOperations<T> operations;
    protected volatile boolean loggedIn;
    protected String fileExpressionResult;
    protected volatile ShutdownRunningTask shutdownRunningTask;
    protected volatile int pendingExchanges;
    protected Processor customProcessor;
    @UriParam
    protected boolean eagerLimitMaxMessagesPerPoll = true;
    protected volatile boolean prepareOnStartup;

    public GenericFileConsumer(GenericFileEndpoint<T> endpoint, Processor processor, GenericFileOperations<T> operations) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.operations = operations;
    }

    public Processor getCustomProcessor() {
        return customProcessor;
    }

    /**
     * Use a custom processor to process the exchange.
     * <p/>
     * Only set this if you need to do custom processing, instead of the regular processing.
     * <p/>
     * This is for example used to browse file endpoints by leveraging the file consumer to poll
     * the directory to gather the list of exchanges. But to avoid processing the files regularly
     * we can use a custom processor.
     *
     * @param processor a custom processor
     */
    public void setCustomProcessor(Processor processor) {
        this.customProcessor = processor;
    }

    public boolean isEagerLimitMaxMessagesPerPoll() {
        return eagerLimitMaxMessagesPerPoll;
    }

    public void setEagerLimitMaxMessagesPerPoll(boolean eagerLimitMaxMessagesPerPoll) {
        this.eagerLimitMaxMessagesPerPoll = eagerLimitMaxMessagesPerPoll;
    }

    /**
     * Poll for files
     */
    protected int poll() throws Exception {
        // must prepare on startup the very first time
        if (!prepareOnStartup) {
            // prepare on startup
            endpoint.getGenericFileProcessStrategy().prepareOnStartup(operations, endpoint);
            prepareOnStartup = true;
        }

        // must reset for each poll
        fileExpressionResult = null;
        shutdownRunningTask = null;
        pendingExchanges = 0;

        // before we poll is there anything we need to check?
        // such as are we connected to the FTP Server still?
        if (!prePollCheck()) {
            log.debug("Skipping poll as pre poll check returned false");
            return 0;
        }

        // gather list of files to process
        List<GenericFile<T>> files = new ArrayList<GenericFile<T>>();
        String name = endpoint.getConfiguration().getDirectory();

        // time how long time it takes to poll
        StopWatch stop = new StopWatch();
        boolean limitHit = !pollDirectory(name, files, 0);
        long delta = stop.stop();
        if (log.isDebugEnabled()) {
            log.debug("Took {} to poll: {}", TimeUtils.printDuration(delta), name);
        }

        // log if we hit the limit
        if (limitHit) {
            log.debug("Limiting maximum messages to poll at {} files as there was more messages in this poll.", maxMessagesPerPoll);
        }

        // sort files using file comparator if provided
        if (endpoint.getSorter() != null) {
            Collections.sort(files, endpoint.getSorter());
        }

        // sort using build in sorters so we can use expressions
        // use a linked list so we can dequeue the exchanges
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

        // use a queue for the exchanges
        Deque<Exchange> q = exchanges;

        // we are not eager limiting, but we have configured a limit, so cut the list of files
        if (!eagerLimitMaxMessagesPerPoll && maxMessagesPerPoll > 0) {
            if (files.size() > maxMessagesPerPoll) {
                log.debug("Limiting maximum messages to poll at {} files as there was more messages in this poll.", maxMessagesPerPoll);
                // must first remove excessive files from the in progress repository
                removeExcessiveInProgressFiles(q, maxMessagesPerPoll);
            }
        }

        // consume files one by one
        int total = exchanges.size();
        if (total > 0) {
            log.debug("Total {} files to consume", total);
        }

        int polledMessages = processBatch(CastUtils.cast(q));

        postPollCheck();

        return polledMessages;
    }

    public int processBatch(Queue<Object> exchanges) {
        int total = exchanges.size();
        int answer = total;

        // limit if needed
        if (maxMessagesPerPoll > 0 && total > maxMessagesPerPoll) {
            log.debug("Limiting to maximum messages to poll {} as there was {} messages in this poll.", maxMessagesPerPoll, total);
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
            boolean started;
            if (customProcessor != null) {
                // use a custom processor
                started = customProcessExchange(exchange, customProcessor);
            } else {
                // process the exchange regular
                started = processExchange(exchange);
            }

            // if we did not start process the file then decrement the counter
            if (!started) {
                answer--;
            }
        }

        // drain any in progress files as we are done with this batch
        removeExcessiveInProgressFiles(CastUtils.cast((Deque<?>) exchanges, Exchange.class), 0);

        return answer;
    }

    /**
     * Drain any in progress files as we are done with this batch
     *
     * @param exchanges  the exchanges
     * @param limit      the limit
     */
    protected void removeExcessiveInProgressFiles(Deque<Exchange> exchanges, int limit) {
        // remove the file from the in progress list in case the batch was limited by max messages per poll
        while (exchanges.size() > limit) {
            // must remove last
            Exchange exchange = exchanges.removeLast();
            GenericFile<?> file = exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE, GenericFile.class);
            String key = file.getAbsoluteFilePath();
            endpoint.getInProgressRepository().remove(key);
        }
    }

    /**
     * Whether or not we can continue polling for more files
     *
     * @param fileList  the current list of gathered files
     * @return <tt>true</tt> to continue, <tt>false</tt> to stop due hitting maxMessagesPerPoll limit
     */
    public boolean canPollMoreFiles(List<?> fileList) {
        // at this point we should not limit if we are not eager
        if (!eagerLimitMaxMessagesPerPoll) {
            return true;
        }

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
     * @param depth the current depth of the directory (will start from 0)
     * @return whether or not to continue polling, <tt>false</tt> means the maxMessagesPerPoll limit has been hit
     */
    protected abstract boolean pollDirectory(String fileName, List<GenericFile<T>> fileList, int depth);

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
     * Whether to ignore if the file cannot be retrieved.
     * <p/>
     * By default an {@link GenericFileOperationFailedException} is thrown if the file cannot be retrieved.
     * <p/>
     * This method allows to suppress this and just ignore that.
     *
     * @param name        the file name
     * @param exchange    the exchange
     * @param cause       optional exception occurred during retrieving file
     * @return <tt>true</tt> to ignore, <tt>false</tt> is the default.
     */
    protected boolean ignoreCannotRetrieveFile(String name, Exchange exchange, Exception cause) {
        return false;
    }

    /**
     * Processes the exchange
     *
     * @param exchange the exchange
     * @return <tt>true</tt> if the file was started to be processed, <tt>false</tt> if the file was not started
     * to be processed, for some reason (not found, or aborted etc)
     */
    protected boolean processExchange(final Exchange exchange) {
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
                log.debug("{} cannot begin processing file: {}", endpoint, file);
                try {
                    // abort
                    processStrategy.abort(operations, endpoint, exchange, file);
                } finally {
                    // begin returned false, so remove file from the in progress list as its no longer in progress
                    endpoint.getInProgressRepository().remove(absoluteFileName);
                }
                return false;
            }
        } catch (Exception e) {
            // remove file from the in progress list due to failure
            endpoint.getInProgressRepository().remove(absoluteFileName);

            String msg = endpoint + " cannot begin processing file: " + file + " due to: " + e.getMessage();
            handleException(msg, e);
            return false;
        }

        // must use file from exchange as it can be updated due the
        // preMoveNamePrefix/preMoveNamePostfix options
        final GenericFile<T> target = getExchangeFileProperty(exchange);
        // must use full name when downloading so we have the correct path
        final String name = target.getAbsoluteFilePath();
        try {
            
            if (isRetrieveFile()) {
                // retrieve the file using the stream
                log.trace("Retrieving file: {} from: {}", name, endpoint);
    
                // retrieve the file and check it was a success
                boolean retrieved;
                Exception cause = null;
                try {
                    retrieved = operations.retrieveFile(name, exchange);
                } catch (Exception e) {
                    retrieved = false;
                    cause = e;
                }

                if (!retrieved) {
                    if (ignoreCannotRetrieveFile(name, exchange, cause)) {
                        log.trace("Cannot retrieve file {} maybe it does not exists. Ignoring.", name);
                        // remove file from the in progress list as we could not retrieve it, but should ignore
                        endpoint.getInProgressRepository().remove(absoluteFileName);
                        return false;
                    } else {
                        // throw exception to handle the problem with retrieving the file
                        // then if the method return false or throws an exception is handled the same in here
                        // as in both cases an exception is being thrown
                        if (cause != null && cause instanceof GenericFileOperationFailedException) {
                            throw cause;
                        } else {
                            throw new GenericFileOperationFailedException("Cannot retrieve file: " + file + " from: " + endpoint, cause);
                        }
                    }
                }
    
                log.trace("Retrieved file: {} from: {}", name, endpoint);                
            } else {
                log.trace("Skipped retrieval of file: {} from: {}", name, endpoint);
                exchange.getIn().setBody(null);
            }

            // register on completion callback that does the completion strategies
            // (for instance to move the file after we have processed it)
            exchange.addOnCompletion(new GenericFileOnCompletion<T>(endpoint, operations, target, absoluteFileName));

            log.debug("About to process file: {} using exchange: {}", target, exchange);

            // process the exchange using the async consumer to support async routing engine
            // which can be supported by this file consumer as all the done work is
            // provided in the GenericFileOnCompletion
            getAsyncProcessor().process(exchange, new AsyncCallback() {
                public void done(boolean doneSync) {
                    // noop
                    if (log.isTraceEnabled()) {
                        log.trace("Done processing file: {} {}", target, doneSync ? "synchronously" : "asynchronously");
                    }
                }
            });

        } catch (Exception e) {
            // remove file from the in progress list due to failure
            // (cannot be in finally block due to GenericFileOnCompletion will remove it
            // from in progress when it takes over and processes the file, which may happen
            // by another thread at a later time. So its only safe to remove it if there was an exception)
            endpoint.getInProgressRepository().remove(absoluteFileName);

            String msg = "Error processing file " + file + " due to " + e.getMessage();
            handleException(msg, e);
        }

        return true;
    }

    /**
     * Override if required.  Files are retrieved / returns true by default
     *
     * @return <tt>true</tt> to retrieve files, <tt>false</tt> to skip retrieval of files.
     */
    protected boolean isRetrieveFile() {
        return true;
    }

    /**
     * Processes the exchange using a custom processor.
     *
     * @param exchange the exchange
     * @param processor the custom processor
     */
    protected boolean customProcessExchange(final Exchange exchange, final Processor processor) {
        GenericFile<T> file = getExchangeFileProperty(exchange);
        log.trace("Custom processing file: {}", file);

        // must extract the absolute name before the begin strategy as the file could potentially be pre moved
        // and then the file name would be changed
        String absoluteFileName = file.getAbsoluteFilePath();

        try {
            // process using the custom processor
            processor.process(exchange);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(endpoint + " error custom processing: " + file + " due to: " + e.getMessage() + ". This exception will be ignored.", e);
            }
            handleException(e);
        } finally {
            // always remove file from the in progress list as its no longer in progress
            // use the original file name that was used to add it to the repository
            // as the name can be different when using preMove option
            endpoint.getInProgressRepository().remove(absoluteFileName);
        }

        return true;
    }

    /**
     * Strategy for validating if the given remote file should be included or not
     *
     * @param file        the file
     * @param isDirectory whether the file is a directory or a file
     * @param files       files in the directory
     * @return <tt>true</tt> to include the file, <tt>false</tt> to skip it
     */
    protected boolean isValidFile(GenericFile<T> file, boolean isDirectory, List<T> files) {
        if (!isMatched(file, isDirectory, files)) {
            log.trace("File did not match. Will skip this file: {}", file);
            return false;
        }

        // if its a file then check if its already in progress
        if (!isDirectory && isInProgress(file)) {
            if (log.isTraceEnabled()) {
                log.trace("Skipping as file is already in progress: {}", file.getFileName());
            }
            return false;
        }

        boolean answer = true;
        String key = null;
        try {
            // if its a file then check we have the file in the idempotent registry already
            if (!isDirectory && endpoint.isIdempotent()) {
                // use absolute file path as default key, but evaluate if an expression key was configured
                key = file.getAbsoluteFilePath();
                if (endpoint.getIdempotentKey() != null) {
                    Exchange dummy = endpoint.createExchange(file);
                    key = endpoint.getIdempotentKey().evaluate(dummy, String.class);
                }
                if (key != null && endpoint.getIdempotentRepository().contains(key)) {
                    log.trace("This consumer is idempotent and the file has been consumed before. Will skip this file: {}", file);
                    answer = false;
                }
            }
        } finally {
            // ensure to run this in finally block in case of runtime exceptions being thrown
            if (!answer) {
                // remove file from the in progress list as its no longer in progress
                endpoint.getInProgressRepository().remove(key);
            }
        }

        // file matched
        return answer;
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
     * @param files       files in the directory
     * @return <tt>true</tt> if the file is matched, <tt>false</tt> if not
     */
    protected boolean isMatched(GenericFile<T> file, boolean isDirectory, List<T> files) {
        String name = file.getFileNameOnly();

        // folders/names starting with dot is always skipped (eg. ".", ".camel", ".camelLock")
        if (name.startsWith(".")) {
            return false;
        }

        // lock files should be skipped
        if (name.endsWith(FileComponent.DEFAULT_LOCK_FILE_POSTFIX)) {
            return false;
        }

        if (endpoint.getFilter() != null) {
            if (!endpoint.getFilter().accept(file)) {
                return false;
            }
        }

        if (endpoint.getAntFilter() != null) {
            if (!endpoint.getAntFilter().accept(file)) {
                return false;
            }
        }

        // directories are regarded as matched if filter accepted them
        if (isDirectory) {
            return true;
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
            fileExpressionResult = evaluateFileExpression();
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

            if (!isMatched(file, doneFileName, files)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Strategy to perform file matching based on endpoint configuration in terms of done file name.
     *
     * @param file         the file
     * @param doneFileName the done file name (without any paths)
     * @param files        files in the directory
     * @return <tt>true</tt> if the file is matched, <tt>false</tt> if not
     */
    protected abstract boolean isMatched(GenericFile<T> file, String doneFileName, List<T> files);

    /**
     * Is the given file already in progress.
     *
     * @param file the file
     * @return <tt>true</tt> if the file is already in progress
     */
    protected boolean isInProgress(GenericFile<T> file) {
        String key = file.getAbsoluteFilePath();
        // must use add, to have operation as atomic
        return !endpoint.getInProgressRepository().add(key);
    }

    protected String evaluateFileExpression() {
        if (fileExpressionResult == null && endpoint.getFileName() != null) {
            // create a dummy exchange as Exchange is needed for expression evaluation
            Exchange dummy = endpoint.createExchange();
            fileExpressionResult = endpoint.getFileName().evaluate(dummy, String.class);
        }
        return fileExpressionResult;
    }

    @SuppressWarnings("unchecked")
    private GenericFile<T> getExchangeFileProperty(Exchange exchange) {
        return (GenericFile<T>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        prepareOnStartup = false;
        super.doStop();
    }
}
