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

package org.apache.camel.impl.remote;

import java.util.List;

import org.apache.camel.spi.ServiceCallLoadBalancer;
import org.apache.camel.spi.ServiceCallServer;

public class RoundRobinServiceCallLoadBalancer<S extends ServiceCallServer> implements ServiceCallLoadBalancer<S> {
    private int counter = -1;

    @Override
    public S chooseServer(List<S> servers) {
        int size = servers.size();
        if (++counter >= size || size == 1) {
            counter = 0;
        }
        return servers.get(counter);
    }

    @Override
    public String toString() {
        return "RoundRobinServiceCallLoadBalancer";
    }
}
