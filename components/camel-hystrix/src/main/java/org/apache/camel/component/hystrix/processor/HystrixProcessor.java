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

import java.util.ArrayList;
import java.util.List;

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.CircuitBreakerConstants;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.AsyncProcessorSupport;

/**
 * Implementation of the Hystrix EIP.
 */
@ManagedResource(description = "Managed Hystrix Processor")
public class HystrixProcessor extends AsyncProcessorSupport implements Navigate<Processor>, org.apache.camel.Traceable, IdAware {

    private String id;
    private final HystrixCommandGroupKey groupKey;
    private final HystrixCommandKey commandKey;
    private final HystrixCommandKey fallbackCommandKey;
    private final com.netflix.hystrix.HystrixCommand.Setter setter;
    private final com.netflix.hystrix.HystrixCommand.Setter fallbackSetter;
    private final Processor processor;
    private final Processor fallback;
    private final boolean fallbackViaNetwork;

    public HystrixProcessor(HystrixCommandGroupKey groupKey, HystrixCommandKey commandKey, HystrixCommandKey fallbackCommandKey,
                            HystrixCommand.Setter setter, HystrixCommand.Setter fallbackSetter,
                            Processor processor, Processor fallback, boolean fallbackViaNetwork) {
        this.groupKey = groupKey;
        this.commandKey = commandKey;
        this.fallbackCommandKey = fallbackCommandKey;
        this.setter = setter;
        this.fallbackSetter = fallbackSetter;
        this.processor = processor;
        this.fallback = fallback;
        this.fallbackViaNetwork = fallbackViaNetwork;
    }

    @ManagedAttribute
    public String getHystrixCommandKey() {
        return commandKey.name();
    }

    @ManagedAttribute
    public String getHystrixFallbackCommandKey() {
        if (fallbackCommandKey != null) {
            return fallbackCommandKey.name();
        } else {
            return null;
        }
    }

    @ManagedAttribute
    public String getHystrixGroupKey() {
        return groupKey.name();
    }

    @ManagedAttribute
    public boolean isFallbackViaNetwork() {
        return isFallbackViaNetwork();
    }

    @ManagedAttribute
    public int getHystrixTotalTimeMean() {
        HystrixCommandMetrics metrics = HystrixCommandMetrics.getInstance(commandKey);
        if (metrics != null) {
            return metrics.getTotalTimeMean();
        }
        return 0;
    }

    @ManagedAttribute
    public int getHystrixExecutionTimeMean() {
        HystrixCommandMetrics metrics = HystrixCommandMetrics.getInstance(commandKey);
        if (metrics != null) {
            return metrics.getExecutionTimeMean();
        }
        return 0;
    }

    @ManagedAttribute
    public int getHystrixCurrentConcurrentExecutionCount() {
        HystrixCommandMetrics metrics = HystrixCommandMetrics.getInstance(commandKey);
        if (metrics != null) {
            return metrics.getCurrentConcurrentExecutionCount();
        }
        return 0;
    }

    @ManagedAttribute
    public long getHystrixTotalRequests() {
        HystrixCommandMetrics metrics = HystrixCommandMetrics.getInstance(commandKey);
        if (metrics != null) {
            return metrics.getHealthCounts().getTotalRequests();
        }
        return 0;
    }

    @ManagedAttribute
    public long getHystrixErrorCount() {
        HystrixCommandMetrics metrics = HystrixCommandMetrics.getInstance(commandKey);
        if (metrics != null) {
            return metrics.getHealthCounts().getErrorCount();
        }
        return 0;
    }

    @ManagedAttribute
    public int getHystrixErrorPercentage() {
        HystrixCommandMetrics metrics = HystrixCommandMetrics.getInstance(commandKey);
        if (metrics != null) {
            return metrics.getHealthCounts().getErrorPercentage();
        }
        return 0;
    }

    @ManagedAttribute
    public boolean isCircuitBreakerOpen() {
        HystrixCircuitBreaker cb = HystrixCircuitBreaker.Factory.getInstance(commandKey);
        if (cb != null) {
            return cb.isOpen();
        }
        return false;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getTraceLabel() {
        return "hystrix";
    }

    @Override
    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<>();
        answer.add(processor);
        if (fallback != null) {
            answer.add(fallback);
        }
        return answer;
    }

    @Override
    public boolean hasNext() {
        return true;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // run this as if we run inside try .. catch so there is no regular Camel error handler
        exchange.setProperty(Exchange.TRY_ROUTE_BLOCK, true);

        try {
            HystrixProcessorCommandFallbackViaNetwork fallbackCommand = null;
            if (fallbackViaNetwork) {
                fallbackCommand = new HystrixProcessorCommandFallbackViaNetwork(fallbackSetter, exchange, fallback);
            }
            HystrixProcessorCommand command = new HystrixProcessorCommand(setter, exchange, processor, fallback, fallbackCommand);
            command.execute();

            // enrich exchange with details from hystrix about the command execution
            commandResponse(exchange, command);

        } catch (Throwable e) {
            exchange.setException(e);
        }

        exchange.removeProperty(Exchange.TRY_ROUTE_BLOCK);
        callback.done(true);
        return true;
    }

    private void commandResponse(Exchange exchange, HystrixCommand command) {
        exchange.setProperty(CircuitBreakerConstants.RESPONSE_SUCCESSFUL_EXECUTION, command.isSuccessfulExecution());
        exchange.setProperty(CircuitBreakerConstants.RESPONSE_FROM_FALLBACK, command.isResponseFromFallback());
        exchange.setProperty(CircuitBreakerConstants.RESPONSE_SHORT_CIRCUITED, command.isResponseShortCircuited());
        exchange.setProperty(CircuitBreakerConstants.RESPONSE_TIMED_OUT, command.isResponseTimedOut());
        exchange.setProperty(CircuitBreakerConstants.RESPONSE_REJECTED, command.isResponseRejected());
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

}
