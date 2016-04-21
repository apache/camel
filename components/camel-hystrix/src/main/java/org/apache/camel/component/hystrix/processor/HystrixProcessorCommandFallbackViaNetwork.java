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
 * Hystrix Command the Camel Hystrix EIP when executing fallback.
 * The fallback may require networking and therefore should run in another Hystrix Command
 */
public class HystrixProcessorCommandFallbackViaNetwork extends HystrixCommand<Message> {

    private static final Logger LOG = LoggerFactory.getLogger(HystrixProcessorCommandFallbackViaNetwork.class);
    private final Exchange exchange;
    private final Processor processor;

    public HystrixProcessorCommandFallbackViaNetwork(Setter setter, Exchange exchange, Processor processor) {
        super(setter);
        this.exchange = exchange;
        this.processor = processor;
    }

    @Override
    protected Message getFallback() {
        return null;
    }

    @Override
    protected Message run() throws Exception {
        LOG.debug("Running fallback processor: {} with exchange: {}", processor, exchange);

        try {
            // process the processor until its fully done
            // (we do not hav any hystrix callback to leverage so we need to complete all work in this run method)
            processor.process(exchange);
        } catch (Throwable e) {
            exchange.setException(e);
        }

        // if we failed then throw an exception to signal that the fallback failed as well
        if (exchange.getException() != null) {
            throw exchange.getException();
        }

        LOG.debug("Running fallback processor: {} with exchange: {} done", processor, exchange);
        // no fallback then we are done
        return exchange.hasOut() ? exchange.getOut() : exchange.getIn();
    }
}
