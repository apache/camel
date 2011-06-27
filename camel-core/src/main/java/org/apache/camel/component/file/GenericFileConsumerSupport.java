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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support class for both a regular {@link org.apache.camel.Consumer}
 * and a {@link org.apache.camel.PollingConsumer} using the file component.
 * <p/>
 * This class contains shared code between the two kind of consumers, to reuse logic.
 * <p/>
 * The method {@link #processExchange(org.apache.camel.Exchange)} should be invoked to
 * process the consumed file. Then custom implementations can implement the strategy
 * method for their custom logic.
 */
public abstract class GenericFileConsumerSupport<T> {
    protected final transient Logger log = LoggerFactory.getLogger(getClass());
    protected final GenericFileConsumer<T> consumer;

    public GenericFileConsumerSupport(GenericFileConsumer<T> consumer) {
        this.consumer = consumer;
    }

    /**
     * Strategy to process the consumed file
     *
     * @param exchange the exchange with the file details
     */
    abstract void processFileStrategy(Exchange exchange);

    /**
     * Strategy to handle the exception thrown that occurred while processing the consumer file
     * <p/>
     * Implementations will usually delegate to a {@link org.apache.camel.spi.ExceptionHandler}
     * to handle the given exception.
     *
     * @param e the caused exception
     */
    abstract void handleExceptionStrategy(Exception e);

    /**
     * Processes the exchange.
     * <p/>
     * This method should be invoked to process the consumed file
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
            final GenericFileProcessStrategy<T> processStrategy = consumer.getEndpoint().getGenericFileProcessStrategy();

            boolean begin = processStrategy.begin(consumer.getOperations(), consumer.getEndpoint(), exchange, file);
            if (!begin) {
                log.debug(consumer.getEndpoint() + " cannot begin processing file: {}", file);
                // begin returned false, so remove file from the in progress list as its no longer in progress
                consumer.getEndpoint().getInProgressRepository().remove(absoluteFileName);
                return;
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug(consumer.getEndpoint() + " cannot begin processing file: " + file + " due to: " + e.getMessage(), e);
            }
            consumer.getEndpoint().getInProgressRepository().remove(absoluteFileName);
            return;
        }

        // must use file from exchange as it can be updated due the
        // preMoveNamePrefix/preMoveNamePostfix options
        final GenericFile<T> target = getExchangeFileProperty(exchange);
        // must use full name when downloading so we have the correct path
        final String name = target.getAbsoluteFilePath();
        try {
            // retrieve the file using the stream
            log.trace("Retrieving file: {} from: {}", name, consumer.getEndpoint());

            // retrieve the file and check it was a success
            boolean retrieved = consumer.getOperations().retrieveFile(name, exchange);
            if (!retrieved) {
                // throw exception to handle the problem with retrieving the file
                // then if the method return false or throws an exception is handled the same in here
                // as in both cases an exception is being thrown
                throw new GenericFileOperationFailedException("Cannot retrieve file: " + file + " from: " + consumer.getEndpoint());
            }

            log.trace("Retrieved file: {} from: {}", name, consumer.getEndpoint());

            // register on completion callback that does the completion strategies
            // (for instance to move the file after we have processed it)
            exchange.addOnCompletion(new GenericFileOnCompletion<T>(consumer.getEndpoint(), consumer.getOperations(), target, absoluteFileName));

            log.debug("About to process file: {} using exchange: {}", target, exchange);

            // process the file
            processFileStrategy(exchange);

        } catch (Exception e) {
            // remove file from the in progress list due to failure
            // (cannot be in finally block due to GenericFileOnCompletion will remove it
            // from in progress when it takes over and processes the file, which may happen
            // by another thread at a later time. So its only safe to remove it if there was an exception)
            consumer.getEndpoint().getInProgressRepository().remove(absoluteFileName);
            handleExceptionStrategy(e);
        }
    }

    @SuppressWarnings("unchecked")
    private GenericFile<T> getExchangeFileProperty(Exchange exchange) {
        return (GenericFile<T>) exchange.getProperty(FileComponent.FILE_EXCHANGE_FILE);
    }

}
