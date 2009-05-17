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
package org.apache.camel.processor.loadbalancer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.util.ObjectHelper;

/**
 * This FailOverLoadBalancer will failover to use next processor when an exception occured
 */
public class FailOverLoadBalancer extends LoadBalancerSupport {

    private final List<Class> exceptions;

    public FailOverLoadBalancer() {
        this.exceptions = null;
    }

    public FailOverLoadBalancer(List<Class> exceptions) {
        this.exceptions = exceptions;
        for (Class type : exceptions) {
            if (!ObjectHelper.isAssignableFrom(Throwable.class, type)) {
                throw new IllegalArgumentException("Class is not an instance of Trowable: " + type);
            }
        }
    }

    /**
     * Should the given failed Exchange failover?
     *
     * @param exchange the exchange that failed
     * @return <tt>true</tt> to failover
     */
    protected boolean shouldFailOver(Exchange exchange) {
        if (exchange.getException() != null) {

            if (exceptions == null || exceptions.isEmpty()) {
                // always failover if no exceptions defined
                return true;
            }

            for (Class exception : exceptions) {
                // use exception iterator to walk the hieracy tree as the exception is possibly wrapped
                Iterator<Throwable> it = ObjectHelper.createExceptionIterator(exchange.getException());
                while (it.hasNext()) {
                    Throwable e = it.next();
                    if (exception.isInstance(e)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void process(Exchange exchange) throws Exception {
        List<Processor> list = getProcessors();
        if (list.isEmpty()) {
            throw new IllegalStateException("No processors available to process " + exchange);
        }

        int index = 0;
        Processor processor = list.get(index);

        // process the first time
        processExchange(processor, exchange);

        // loop while we should fail over
        while (shouldFailOver(exchange)) {
            index++;
            if (index < list.size()) {
                // try again but reset exception first
                exchange.setException(null);
                processor = list.get(index);
                processExchange(processor, exchange);
            } else {
                // no more processors to try
                break;
            }
        }
    }

    private void processExchange(Processor processor, Exchange exchange) {
        if (processor == null) {
            throw new IllegalStateException("No processors could be chosen to process " + exchange);
        }
        try {
            processor.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
    }

}
