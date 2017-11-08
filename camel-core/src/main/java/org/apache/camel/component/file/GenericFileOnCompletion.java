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

import org.apache.camel.Exchange;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.LoggingExceptionHandler;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * On completion strategy that performs the required work after the {@link Exchange} has been processed.
 * <p/>
 * The work is for example to move the processed file into a backup folder, delete the file or
 * in case of processing failure do a rollback. 
 *
 * @version 
 */
public class GenericFileOnCompletion<T> implements Synchronization {

    private final Logger log = LoggerFactory.getLogger(GenericFileOnCompletion.class);
    private GenericFileEndpoint<T> endpoint;
    private GenericFileOperations<T> operations;
    private ExceptionHandler exceptionHandler;
    private GenericFile<T> file;
    private String absoluteFileName;

    public GenericFileOnCompletion(GenericFileEndpoint<T> endpoint, GenericFileOperations<T> operations,
                                   GenericFile<T> file, String absoluteFileName) {
        this.endpoint = endpoint;
        this.operations = operations;
        this.file = file;
        this.absoluteFileName = absoluteFileName;
        this.exceptionHandler = endpoint.getOnCompletionExceptionHandler();
        if (this.exceptionHandler == null) {
            this.exceptionHandler = new LoggingExceptionHandler(endpoint.getCamelContext(), getClass());
        }
    }

    public void onComplete(Exchange exchange) {
        onCompletion(exchange);
    }

    public void onFailure(Exchange exchange) {
        onCompletion(exchange);
    }

    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    protected void onCompletion(Exchange exchange) {
        GenericFileProcessStrategy<T> processStrategy = endpoint.getGenericFileProcessStrategy();

        log.debug("Done processing file: {} using exchange: {}", file, exchange);

        // commit or rollback
        boolean committed = false;
        try {
            boolean failed = exchange.isFailed();
            if (!failed) {
                // commit the file strategy if there was no failure or already handled by the DeadLetterChannel
                processStrategyCommit(processStrategy, exchange, file);
                committed = true;
            }
            // if we failed, then it will be handled by the rollback in the finally block below
        } finally {
            if (!committed) {
                processStrategyRollback(processStrategy, exchange, file);
            }

            // remove file from the in progress list as its no longer in progress
            // use the original file name that was used to add it to the repository
            // as the name can be different when using preMove option
            endpoint.getInProgressRepository().remove(absoluteFileName);
        }
    }

    /**
     * Strategy when the file was processed and a commit should be executed.
     *
     * @param processStrategy the strategy to perform the commit
     * @param exchange        the exchange
     * @param file            the file processed
     */
    protected void processStrategyCommit(GenericFileProcessStrategy<T> processStrategy,
                                         Exchange exchange, GenericFile<T> file) {
        if (endpoint.isIdempotent()) {

            // use absolute file path as default key, but evaluate if an expression key was configured
            String key = absoluteFileName;
            if (endpoint.getIdempotentKey() != null) {
                Exchange dummy = endpoint.createExchange(file);
                key = endpoint.getIdempotentKey().evaluate(dummy, String.class);
            }

            // only add to idempotent repository if we could process the file
            if (key != null) {
                endpoint.getIdempotentRepository().add(key);
            }
        }

        handleDoneFile(exchange);

        try {
            log.trace("Commit file strategy: {} for file: {}", processStrategy, file);
            processStrategy.commit(operations, endpoint, exchange, file);
        } catch (Exception e) {
            handleException("Error during commit", exchange, e);
        }
    }

    /**
     * Strategy when the file was not processed and a rollback should be executed.
     *
     * @param processStrategy the strategy to perform the commit
     * @param exchange        the exchange
     * @param file            the file processed
     */
    protected void processStrategyRollback(GenericFileProcessStrategy<T> processStrategy,
                                           Exchange exchange, GenericFile<T> file) {

        if (log.isWarnEnabled()) {
            log.warn("Rollback file strategy: {} for file: {}", processStrategy, file);
        }

        // only delete done file if moveFailed option is enabled, as otherwise on rollback,
        // we should leave the done file so we can retry
        if (endpoint.getMoveFailed() != null) {
            handleDoneFile(exchange);
        }

        try {
            processStrategy.rollback(operations, endpoint, exchange, file);
        } catch (Exception e) {
            handleException("Error during rollback", exchange, e);
        }
    }

    protected void handleDoneFile(Exchange exchange) {
        // must be last in batch to delete the done file name
        // delete done file if used (and not noop=true)
        boolean complete = exchange.getProperty(Exchange.BATCH_COMPLETE, false, Boolean.class);
        if (endpoint.getDoneFileName() != null && !endpoint.isNoop()) {
            // done file must be in same path as the original input file
            String doneFileName = endpoint.createDoneFileName(absoluteFileName);
            ObjectHelper.notEmpty(doneFileName, "doneFileName", endpoint);
            // we should delete the dynamic done file
            if (endpoint.getDoneFileName().indexOf("{file:name") > 0 || complete) {
                try {
                    // delete done file
                    boolean deleted = operations.deleteFile(doneFileName);
                    log.trace("Done file: {} was deleted: {}", doneFileName, deleted);
                    if (!deleted) {
                        log.warn("Done file: {} could not be deleted", doneFileName);
                    }
                } catch (Exception e) {
                    handleException("Error deleting done file: " + doneFileName, exchange, e);
                }
            }
        }
    }

    protected void handleException(String message, Exchange exchange, Throwable t) {
        Throwable newt = (t == null) ? new IllegalArgumentException("Handling [null] exception") : t;
        getExceptionHandler().handleException(message, exchange, newt);
    }

    @Override
    public String toString() {
        return "GenericFileOnCompletion";
    }
}
