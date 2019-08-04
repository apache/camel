## ------------------------------------------------------------------------
## Licensed to the Apache Software Foundation (ASF) under one or more
## contributor license agreements.  See the NOTICE file distributed with
## this work for additional information regarding copyright ownership.
## The ASF licenses this file to You under the Apache License, Version 2.0
## (the "License"); you may not use this file except in compliance with
## the License.  You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software
## distributed under the License is distributed on an "AS IS" BASIS,
## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
## See the License for the specific language governing permissions and
## limitations under the License.
## ------------------------------------------------------------------------
package ${package};

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.DefaultConsumer;

import java.util.concurrent.ExecutorService;

/**
 * The ${name} consumer.
 */
public class ${name}Consumer extends DefaultConsumer {
    private final ${name}Endpoint endpoint;
    private final EventBusHelper eventBusHelper;

    private ExecutorService executorService;

    public ${name}Consumer(${name}Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        eventBusHelper = EventBusHelper.getInstance();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // start a single threaded pool to monitor events
        executorService = endpoint.createExecutor();

        // submit task to the thread pool
        executorService.submit(() -> {
            // subscribe to an event
            eventBusHelper.subscribe(this::onEventListener);
        });
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        // shutdown the thread pool gracefully
        getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);
    }

    private void onEventListener(final Object event) {
        final Exchange exchange = endpoint.createExchange();

        exchange.getIn().setBody("Hello World! The time is " + event);

        try {
            // send message to next processor in the route
            getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }
}
