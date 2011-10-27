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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;

/**
 * A default base class for a {@link LoadBalancer} implementation.
 * <p/>
 * This implementation is dedicated for simple synchronous load balancers.
 * <p/>
 * Consider using the {@link LoadBalancerSupport} if you want to support
 * the asynchronous routing engine in Camel.
 *
 * @version 
 */
public abstract class SimpleLoadBalancerSupport extends LoadBalancerSupport {

    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        callback.done(true);
        return true;
    }

    public abstract void process(Exchange exchange) throws Exception;
}