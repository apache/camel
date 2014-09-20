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

/**
 * A base class for {@link LoadBalancer} implementations which choose a single
 * destination for each exchange (rather like JMS Queues)
 * 
 * @version 
 */
public abstract class QueueLoadBalancer extends LoadBalancerSupport {

    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        List<Processor> list = getProcessors();
        if (!list.isEmpty()) {
            Processor processor = chooseProcessor(list, exchange);
            if (processor == null) {
                Exception e = new IllegalStateException("No processors could be chosen to process " + exchange);
                exchange.setException(e);
            } else {
                if (processor instanceof AsyncProcessor) {
                    AsyncProcessor async = (AsyncProcessor) processor;
                    return async.process(exchange, callback);
                } else {
                    try {
                        processor.process(exchange);
                    } catch (Exception e) {
                        exchange.setException(e);
                    }
                    callback.done(true);
                    return true;
                }
            }
        }

        // no processors but indicate we are done
        callback.done(true);
        return true;
    }

    protected abstract Processor chooseProcessor(List<Processor> processors, Exchange exchange);
}
