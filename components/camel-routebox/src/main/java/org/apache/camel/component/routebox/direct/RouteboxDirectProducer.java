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
package org.apache.camel.component.routebox.direct;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.routebox.RouteboxServiceSupport;
import org.apache.camel.component.routebox.strategy.RouteboxDispatcher;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class RouteboxDirectProducer extends RouteboxServiceSupport implements Producer, AsyncProcessor {
    private static final transient Log LOG = LogFactory.getLog(RouteboxDirectProducer.class);
    protected ProducerTemplate producer;

    public RouteboxDirectProducer(RouteboxDirectEndpoint endpoint) {
        super(endpoint);
        producer = endpoint.getConfig().getInnerProducerTemplate();
    }

    public void process(Exchange exchange) throws Exception {
        Exchange result;
        
        if ((((RouteboxDirectEndpoint)getRouteboxEndpoint()).getConsumer() == null) && (getRouteboxEndpoint().getConfig().isSendToConsumer())) {
            throw new CamelExchangeException("No consumers available on endpoint: " + getRouteboxEndpoint(), exchange);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Dispatching to Inner Route " + exchange);
            }
            RouteboxDispatcher dispatcher = new RouteboxDispatcher(producer);
            result = dispatcher.dispatchSync(getRouteboxEndpoint(), exchange);
        }
        if (getRouteboxEndpoint().getConfig().isSendToConsumer()) {
            ((RouteboxDirectEndpoint)getRouteboxEndpoint()).getConsumer().getProcessor().process(result);
        }
    }

    public boolean process(Exchange exchange, final AsyncCallback callback) {
        boolean flag = true;
        
        if ((((RouteboxDirectEndpoint)getRouteboxEndpoint()).getConsumer() == null) 
            && ((getRouteboxEndpoint()).getConfig().isSendToConsumer())) {
            exchange.setException(new CamelExchangeException("No consumers available on endpoint: " + getRouteboxEndpoint(), exchange));
            callback.done(true);
            flag = true;
        } else {
            try {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Dispatching to Inner Route " + exchange);
                }
                
                RouteboxDispatcher dispatcher = new RouteboxDispatcher(producer);
                exchange = dispatcher.dispatchAsync(getRouteboxEndpoint(), exchange);      
                if (getRouteboxEndpoint().getConfig().isSendToConsumer()) {
                    AsyncProcessor processor = AsyncProcessorTypeConverter.convert(((RouteboxDirectEndpoint)getRouteboxEndpoint()).getConsumer().getProcessor());
                    flag = AsyncProcessorHelper.process(processor, exchange, new AsyncCallback() {
                        public void done(boolean doneSync) {
                            // we only have to handle async completion of this policy
                            if (doneSync) {
                                return;
                            }
                            callback.done(false);
                        }
                    });
                } 
                callback.done(true);
            } catch (Exception e) {
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
            }
        }
        return flag;
    }
    
    protected void doStart() throws Exception {
        if (!(getRouteboxEndpoint()).getConfig().isSendToConsumer()) {
            // start an inner context
            if (!isStartedInnerContext()) {
                doStartInnerContext(); 
            }
        }
    }

    protected void doStop() throws Exception {
        if (!(getRouteboxEndpoint()).getConfig().isSendToConsumer()) {
            // stop the inner context
            if (isStartedInnerContext()) {
                doStopInnerContext();
            }
        }
    }
    
    public Endpoint getEndpoint() {
        return getRouteboxEndpoint();
    }

    public Exchange createExchange() {
        return getRouteboxEndpoint().createExchange();
    }

    public Exchange createExchange(ExchangePattern pattern) {
        return getRouteboxEndpoint().createExchange(pattern);
    }

    public Exchange createExchange(Exchange exchange) {
        return getRouteboxEndpoint().createExchange(exchange);
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    public String toString() {
        return "Producer[" + getRouteboxEndpoint().getEndpointUri() + "]";
    }

}
