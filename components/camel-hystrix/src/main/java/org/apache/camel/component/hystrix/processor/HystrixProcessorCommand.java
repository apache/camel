/*
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
package org.apache.camel.component.hystrix.processor;

import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.ExchangeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hystrix Command for the Camel Hystrix EIP.
 */
public class HystrixProcessorCommand extends HystrixCommand {

    private static final Logger LOG = LoggerFactory.getLogger(HystrixProcessorCommand.class);
    private final Exchange exchange;
    private final Processor processor;
    private final Processor fallback;
    private final HystrixProcessorCommandFallbackViaNetwork fallbackCommand;
    private final AtomicBoolean fallbackInUse = new AtomicBoolean();
    private final Object lock = new Object();

    public HystrixProcessorCommand(Setter setter, Exchange exchange, Processor processor, Processor fallback,
                                   HystrixProcessorCommandFallbackViaNetwork fallbackCommand) {
        super(setter);
        this.exchange = exchange;
        this.processor = processor;
        this.fallback = fallback;
        this.fallbackCommand = fallbackCommand;
    }

    @Override
    protected Message getFallback() {
        // if bad request then break-out
        if (exchange.getException() instanceof HystrixBadRequestException) {
            return null;
        }

        // guard by lock as the run command can be running concurrently in case hystrix caused a timeout which
        // can cause the fallback timer to trigger this fallback at the same time the run command may be running
        // after its processor.process method which could cause both threads to mutate the state on the exchange
        synchronized (lock) {
            fallbackInUse.set(true);
        }

        if (fallback == null && fallbackCommand == null) {
            // no fallback in use
            throw new UnsupportedOperationException("No fallback available.");
        }

        // grab the exception that caused the error (can be failure in run, or from hystrix if short circuited)
        Throwable exception = getExecutionException();

        if (exception != null) {
            LOG.debug("Error occurred processing. Will now run fallback. Exception class: {} message: {}.", exception.getClass().getName(), exception.getMessage());
        } else {
            LOG.debug("Error occurred processing. Will now run fallback.");
        }
        // store the last to endpoint as the failure endpoint
        if (exchange.getProperty(Exchange.FAILURE_ENDPOINT) == null) {
            exchange.setProperty(Exchange.FAILURE_ENDPOINT, exchange.getProperty(Exchange.TO_ENDPOINT));
        }
        // give the rest of the pipeline another chance
        exchange.setProperty(Exchange.EXCEPTION_HANDLED, true);
        exchange.setProperty(Exchange.EXCEPTION_CAUGHT, exception);
        exchange.setRouteStop(false);
        exchange.setException(null);
        // and we should not be regarded as exhausted as we are in a try .. catch block
        exchange.removeProperty(Exchange.REDELIVERY_EXHAUSTED);
        // run the fallback processor
        try {
            // use fallback command if provided (fallback via network)
            if (fallbackCommand != null) {
                return fallbackCommand.execute();
            } else {
                LOG.debug("Running fallback: {} with exchange: {}", fallback, exchange);
                // process the fallback until its fully done
                // (we do not hav any hystrix callback to leverage so we need to complete all work in this run method)
                fallback.process(exchange);
                LOG.debug("Running fallback: {} with exchange: {} done", fallback, exchange);
            }
        } catch (Exception e) {
            exchange.setException(e);
        }

        return exchange.getMessage();
    }

    @Override
    protected Message run() throws Exception {
        LOG.debug("Running processor: {} with exchange: {}", processor, exchange);

        // prepare a copy of exchange so downstream processors don't cause side-effects if they mutate the exchange
        // in case Hystrix timeout processing and continue with the fallback etc
        Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, false, false);
        try {
            // process the processor until its fully done
            // (we do not hav any hystrix callback to leverage so we need to complete all work in this run method)
            processor.process(copy);
        } catch (Exception e) {
            copy.setException(e);
        }

        // if hystrix execution timeout is enabled and fallback is enabled and a timeout occurs
        // then a hystrix timer thread executes the fallback so we can stop run() execution
        if (getProperties().executionTimeoutEnabled().get()
                && getProperties().fallbackEnabled().get()
                && isCommandTimedOut.get() == TimedOutStatus.TIMED_OUT) {
            LOG.debug("Exiting run command due to a hystrix execution timeout in processing exchange: {}", exchange);
            return null;
        }

        // when a hystrix timeout occurs then a hystrix timer thread executes the fallback
        // and therefore we need this thread to not do anymore if fallback is already in process
        if (fallbackInUse.get()) {
            LOG.debug("Exiting run command as fallback is already in use processing exchange: {}", exchange);
            return null;
        }

        // remember any hystrix execution exception which for example can be triggered by a hystrix timeout
        Throwable hystrixExecutionException = getExecutionException();
        Exception camelExchangeException = copy.getException();

        synchronized (lock) {
            // when a hystrix timeout occurs then a hystrix timer thread executes the fallback
            // and therefore we need this thread to not do anymore if fallback is already in process
            if (fallbackInUse.get()) {
                LOG.debug("Exiting run command as fallback is already in use processing exchange: {}", exchange);
                return null;
            }

            // execution exception must take precedence over exchange exception
            // because hystrix may have caused this command to fail due timeout or something else
            if (hystrixExecutionException != null) {
                exchange.setException(new CamelExchangeException("Hystrix execution exception occurred while processing Exchange", exchange, hystrixExecutionException));
            }

            // special for HystrixBadRequestException which should not trigger fallback
            if (camelExchangeException instanceof HystrixBadRequestException) {
                LOG.debug("Running processor: {} with exchange: {} done as bad request", processor, exchange);
                exchange.setException(camelExchangeException);
                throw camelExchangeException;
            }

            // copy the result before its regarded as success
            ExchangeHelper.copyResults(exchange, copy);

            // in case of an exception in the exchange
            // we need to trigger this by throwing the exception so hystrix will execute the fallback
            // or open the circuit
            if (hystrixExecutionException == null && camelExchangeException != null) {
                throw camelExchangeException;
            }

            LOG.debug("Running processor: {} with exchange: {} done", processor, exchange);
            return exchange.getMessage();
        }
    }

}
