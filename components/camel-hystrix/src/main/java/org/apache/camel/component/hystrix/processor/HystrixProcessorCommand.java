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
package org.apache.camel.component.hystrix.processor;

import com.netflix.hystrix.HystrixCommand;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
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
        // grab the exception that caused the error (can be failure in run, or from hystrix if short circuited)
        Throwable exception = getExecutionException();

        if (fallback != null || fallbackCommand != null) {
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
        }

        return exchange.hasOut() ? exchange.getOut() : exchange.getIn();
    }

    @Override
    protected Message run() throws Exception {
        LOG.debug("Running processor: {} with exchange: {}", processor, exchange);

        try {
            // process the processor until its fully done
            // (we do not hav any hystrix callback to leverage so we need to complete all work in this run method)
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }

        // is fallback enabled
        Boolean fallbackEnabled = getProperties().fallbackEnabled().get();

        // if we failed then throw an exception if fallback is enabled
        if (fallbackEnabled == null || fallbackEnabled && exchange.getException() != null) {
            throw exchange.getException();
        }

        LOG.debug("Running processor: {} with exchange: {} done", processor, exchange);
        // no fallback then we are done
        return exchange.hasOut() ? exchange.getOut() : exchange.getIn();
    }
}
