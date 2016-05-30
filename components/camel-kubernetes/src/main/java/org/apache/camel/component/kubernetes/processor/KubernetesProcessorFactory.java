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

import java.util.Map;
import java.util.Optional;

import org.apache.camel.ExchangePattern;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.impl.remote.DefaultServiceCallProcessor;
import org.apache.camel.impl.remote.DefaultServiceCallProcessorFactory;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.ServiceCallServer;
import org.apache.camel.spi.ServiceCallServerListStrategy;
import org.apache.camel.util.ObjectHelper;

/**
 * {@link ProcessorFactory} that creates the Kubernetes implementation of the ServiceCall EIP.
 */
public class KubernetesProcessorFactory extends DefaultServiceCallProcessorFactory<KubernetesConfiguration, ServiceCallServer> {

    @Override
    protected KubernetesConfiguration createConfiguration(RouteContext routeContext) throws Exception {
        return new KubernetesConfiguration();
    }

    @Override
    protected DefaultServiceCallProcessor createProcessor(
            String name,
            String component,
            String uri,
            ExchangePattern mep,
            KubernetesConfiguration conf,
            Map<String, String> properties) throws Exception {

        return new KubernetesServiceCallProcessor(name, component, uri, mep, conf);
    }

    @Override
    protected Optional<ServiceCallServerListStrategy> builtInServerListStrategy(KubernetesConfiguration conf, String name) throws Exception {
        ServiceCallServerListStrategy strategy = null;
        if (ObjectHelper.equal("client", name, true)) {
            strategy = new KubernetesServiceCallServerListStrategies.Client(conf);
        } else if (ObjectHelper.equal("environment", name, true)) {
            strategy = new KubernetesServiceCallServerListStrategies.Environment(conf);
        } else if (ObjectHelper.equal("env", name, true)) {
            strategy = new KubernetesServiceCallServerListStrategies.Environment(conf);
        } else if (ObjectHelper.equal("dns", name, true)) {
            strategy = new KubernetesServiceCallServerListStrategies.DNS(conf);
        }

        return Optional.ofNullable(strategy);
    }

    @Override
    protected ServiceCallServerListStrategy<ServiceCallServer> createDefaultServerListStrategy(KubernetesConfiguration conf) throws Exception {
        return new KubernetesServiceCallServerListStrategies.Client(conf);
    }
}
