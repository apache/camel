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
package org.apache.camel.component.consul.processor.remote;

import java.util.Map;
import java.util.Optional;

import org.apache.camel.ExchangePattern;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.impl.remote.DefaultServiceCallProcessor;
import org.apache.camel.impl.remote.DefaultServiceCallProcessorFactory;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.ServiceCallServer;
import org.apache.camel.spi.ServiceCallServerListStrategy;
import org.apache.camel.util.ObjectHelper;

/**
 * {@link ProcessorFactory} that creates the Consul implementation of the ServiceCall EIP.
 */
public class ConsulServiceCallProcessorFactory extends DefaultServiceCallProcessorFactory<ConsulConfiguration, ServiceCallServer> {
    @Override
    protected ConsulConfiguration createConfiguration(RouteContext routeContext) throws Exception {
        return new ConsulConfiguration(routeContext.getCamelContext());
    }

    @Override
    protected DefaultServiceCallProcessor createProcessor(
            String name,
            String component,
            String uri,
            ExchangePattern mep,
            ConsulConfiguration conf,
            Map<String, String> properties) throws Exception {

        return new ConsulServiceCallProcessor(name, component, uri, mep, conf);
    }

    @Override
    protected Optional<ServiceCallServerListStrategy> builtInServerListStrategy(ConsulConfiguration conf, String name) throws Exception {
        ServiceCallServerListStrategy strategy = null;
        if (ObjectHelper.equal("ondemand", name, true)) {
            strategy = new ConsulServiceCallServerListStrategies.OnDemand(conf);
        }

        return Optional.ofNullable(strategy);
    }

    @Override
    protected ServiceCallServerListStrategy<ServiceCallServer> createDefaultServerListStrategy(ConsulConfiguration conf) throws Exception {
        return new ConsulServiceCallServerListStrategies.OnDemand(conf);
    }
}
