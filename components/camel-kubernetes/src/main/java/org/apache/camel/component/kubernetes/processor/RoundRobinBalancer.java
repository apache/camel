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
package org.apache.camel.component.kubernetes.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.camel.spi.ServiceCallLoadBalancer;

public class RoundRobinBalancer implements ServiceCallLoadBalancer<KubernetesServer> {

    private int counter = -1;

    @Override
    public KubernetesServer chooseServer(Collection<KubernetesServer> servers) {
        List<KubernetesServer> list = new ArrayList<>(servers);

        int size = list.size();
        if (++counter >= size) {
            counter = 0;
        }
        return list.get(counter);
    }

    @Override
    public String toString() {
        return "RoundRobinBalancer";
    }
}
