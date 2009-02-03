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

import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.ObjectHelper;

/**
 * This FailOverLoadBalancer will choose other processor when the exception happens
 */
public class FailOverLoadBalancer extends LoadBalancerSupport {
    private final Class failException;
    public FailOverLoadBalancer(Class throwable) {
        if (ObjectHelper.isAssignableFrom(Throwable.class, throwable)) {
            failException = throwable;
        } else {
            throw new RuntimeCamelException("The FailOverLoadBalancer construction parameter should be the child of Throwable");
        }
    }
    
    public FailOverLoadBalancer() {
        this(Throwable.class);
    }
    
    protected boolean isCheckedException(Exchange exchange) {
        if (exchange.getException() != null) {
            if (failException.isAssignableFrom(exchange.getException().getClass())) {        
                return true;
            }
        } 
        return false;                   
    }
    
    private void processExchange(Processor processor, Exchange exchange) {
        if (processor == null) {
            throw new IllegalStateException("No processors could be chosen to process " + exchange);
        }
        try {            
            processor.process(exchange);
        } catch (Throwable error) {
            exchange.setException(error);
        }
    }

    public void process(Exchange exchange) throws Exception {
        List<Processor> list = getProcessors();
        if (list.isEmpty()) {
            throw new IllegalStateException("No processors available to process " + exchange);
        }
        int index = 0;
        Processor processor = list.get(index);
        processExchange(processor, exchange);
        while (isCheckedException(exchange)) {
            exchange.setException(null);
            index++;
            if (index < list.size()) {
                processor = list.get(index);
                processExchange(processor, exchange);
            } else {
                break;
            }
        }
    }

    public boolean process(Exchange exchange, final AsyncCallback callback) {
        return processExchange(0, exchange, callback);
    }
    
    public boolean processExchange(final int index, final Exchange exchange, final AsyncCallback callback) {
        boolean sync = false;
        List<Processor> list = getProcessors();
        if (list.isEmpty()) {
            throw new IllegalStateException("No processors available to process " + exchange);
        }       
        Processor processor = list.get(index);
        if (processor == null) {
            throw new IllegalStateException("No processors could be chosen to process " + exchange);
        } else {
            if (processor instanceof AsyncProcessor) {
                AsyncProcessor asyncProcessor = (AsyncProcessor)processor;
                sync = asyncProcessor.process(exchange, new AsyncCallback() {
                    public void done(boolean doSync) {                        
                        // check the exchange and call the FailOverProcessor
                        if (isCheckedException(exchange) && index < getProcessors().size() - 1) {
                            exchange.setException(null);
                            processExchange(index + 1, exchange, callback);                            
                        } else {
                            callback.done(doSync);
                        }
                    }
                });               
                
            } else {
                try {
                    processor.process(exchange);
                } catch (Exception ex) {
                    exchange.setException(ex);
                }
                if (isCheckedException(exchange) && index < getProcessors().size() - 1) {
                    exchange.setException(null);
                    processExchange(index + 1, exchange, callback);
                }
                sync = true;
                callback.done(true);                
            }            
        }
        return sync;
    }

}
