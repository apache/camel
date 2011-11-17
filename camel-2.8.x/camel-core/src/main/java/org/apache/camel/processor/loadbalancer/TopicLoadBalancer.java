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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * A {@link LoadBalancer} implementations which sends to all destinations
 * (rather like JMS Topics).
 * <p/>
 * The {@link org.apache.camel.processor.MulticastProcessor} is more powerful as it offers
 * option to run in parallel and decide whether or not to stop on failure etc.
 *
 * @version 
 */
public class TopicLoadBalancer extends LoadBalancerSupport {

    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        List<Processor> list = getProcessors();
        // too hard to do multiple async, so we do it sync
        for (Processor processor : list) {
            try {
                Exchange copy = copyExchangeStrategy(processor, exchange);
                processor.process(copy);
            } catch (Throwable e) {
                exchange.setException(e);
                // stop on failure
                break;
            }
        }
        callback.done(true);
        return true;
    }

    /**
     * Strategy method to copy the exchange before sending to another endpoint.
     * Derived classes such as the {@link org.apache.camel.processor.Pipeline Pipeline}
     * will not clone the exchange
     *
     * @param processor the processor that will send the exchange
     * @param exchange  the exchange
     * @return the current exchange if no copying is required such as for a
     *         pipeline otherwise a new copy of the exchange is returned.
     */
    protected Exchange copyExchangeStrategy(Processor processor, Exchange exchange) {
        return exchange.copy();
    }

    public String toString() {
        return "TopicLoadBalancer";
    }
}
