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
import org.apache.camel.impl.LoggingExceptionHandler;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.Synchronization;
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

    private final transient Logger log = LoggerFactory.getLogger(GenericFileOnCompletion.class);
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
    }

    public void onComplete(Exchange exchange) {
        onCompletion(exchange);
    }

    public void onFailure(Exchange exchange) {
        onCompletion(exchange);
    }

    public ExceptionHandler getExceptionHandler() {
        if (exceptionHandler == null) {
            exceptionHandler = new LoggingExceptionHandler(getClass());
        }
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
            // only add to idempotent repository if we could process the file
            endpoint.getIdempotentRepository().add(absoluteFileName);
        }

        // delete done file if used
        if (endpoint.getDoneFileName() != null) {
            // done file must be in same path as the original input file
            String doneFileName = endpoint.createDoneFileName(absoluteFileName);
            ObjectHelper.notEmpty(doneFileName, "doneFileName", endpoint);

            try {
                // delete done file
                boolean deleted = operations.deleteFile(doneFileName);
                log.trace("Done file: {} was deleted: {}", doneFileName, deleted);
                if (!deleted) {
                    log.warn("Done file: " + doneFileName + " could not be deleted");
                }
            } catch (Exception e) {
                handleException(e);
            }
        }

        try {
            log.trace("Commit file strategy: {} for file: {}", processStrategy, file);
            processStrategy.commit(operations, endpoint, exchange, file);
        } catch (Exception e) {
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
    protected void processStrategyRollback(GenericFileProcessStrategy<T> processStrategy,
                                           Exchange exchange, GenericFile<T> file) {

        if (log.isWarnEnabled()) {
            log.warn("Rollback file strategy: " + processStrategy + " for file: " + file);
        }
        try {
            processStrategy.rollback(operations, endpoint, exchange, file);
        } catch (Exception e) {
            handleException(e);
        }
    }

    protected void handleException(Throwable t) {
        Throwable newt = (t == null) ? new IllegalArgumentException("Handling [null] exception") : t;
        getExceptionHandler().handleException(newt);
    }

    @Override
    public String toString() {
        return "GenericFileOnCompletion";
    }
}
