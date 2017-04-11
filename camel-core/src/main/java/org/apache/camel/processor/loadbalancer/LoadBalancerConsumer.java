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

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;

/**
 * Represents a consumer which on starting registers itself with a {@link LoadBalancer} and on closing unregisters
 * itself with a load balancer
 *
 * @version 
 */
public class LoadBalancerConsumer extends DefaultConsumer {
    private final LoadBalancer loadBalancer;

    public LoadBalancerConsumer(Endpoint endpoint, Processor processor, LoadBalancer loadBalancer) {
        super(endpoint, processor);
        this.loadBalancer = loadBalancer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        loadBalancer.addProcessor(getProcessor());
    }

    @Override
    protected void doStop() throws Exception {
        loadBalancer.removeProcessor(getProcessor());
        super.doStop();
    }
}
