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
package org.apache.camel.spring.cloud.netflix;

import java.util.List;

import com.netflix.loadbalancer.Server;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceLoadBalancer;
import org.apache.camel.cloud.ServiceLoadBalancerFunction;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.netflix.ribbon.RibbonLoadBalancerClient;
import org.springframework.core.convert.ConversionService;

public class CamelCloudNetflixServiceLoadBalancer implements ServiceLoadBalancer {
    private final LoadBalancerClient client;
    private final List<ConversionService> conversionServices;

    public CamelCloudNetflixServiceLoadBalancer(LoadBalancerClient client, List<ConversionService> conversionServices) {
        this.client = client;
        this.conversionServices = conversionServices;
    }

    @Override
    public <T> T process(String serviceName, ServiceLoadBalancerFunction<T> function) throws Exception {
        return client.execute(serviceName, instance -> {
            ServiceDefinition definition = null;

            //
            // this should not be needed but there is a bug or misbehavior on
            // spring cloud netflix side (2.x) that prevent ribbon load balancer
            // to propagate metadata from i.e. consul, see:
            //
            //     https://github.com/spring-cloud/spring-cloud-consul/issues/424
            //
            // so here we do try to find a converter that is able to use the
            // underlying server implementation to extract meta-data and any
            // other thing needed by Camel.
            //

            if (instance instanceof RibbonLoadBalancerClient.RibbonServer) {
                Server server = RibbonLoadBalancerClient.RibbonServer.class.cast(instance).getServer();

                for (int i = 0; i < conversionServices.size(); i++) {
                    ConversionService cs = conversionServices.get(i);

                    if (cs.canConvert(server.getClass(), ServiceDefinition.class)) {
                        definition = cs.convert(server, ServiceDefinition.class);

                        if (definition != null) {
                            break;
                        }
                    }
                }
            }

            // If no conversion is possible we use the info found on service
            // instance given by the load balancer as it is so the result may
            // be incomplete

            if (definition == null) {
                definition = new DefaultServiceDefinition(
                    instance.getServiceId(),
                    instance.getHost(),
                    instance.getPort(),
                    instance.getMetadata()
                );
            }

            return function.apply(definition);
        });
    }
}
