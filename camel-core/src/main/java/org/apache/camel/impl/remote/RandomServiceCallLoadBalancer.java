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
import java.util.Random;

import org.apache.camel.spi.ServiceCallLoadBalancer;
import org.apache.camel.spi.ServiceCallServer;

public class RandomServiceCallLoadBalancer implements ServiceCallLoadBalancer<ServiceCallServer> {
    private final Random random;

    public RandomServiceCallLoadBalancer() {
        this.random = new Random();
    }

    @Override
    public ServiceCallServer chooseServer(List<ServiceCallServer> servers) {
        int size = servers.size();
        int index = (size > 1) ? random.nextInt(size) : 0;

        return servers.get(index);
    }

    @Override
    public String toString() {
        return "RandomServiceCallLoadBalancer";
    }
}
