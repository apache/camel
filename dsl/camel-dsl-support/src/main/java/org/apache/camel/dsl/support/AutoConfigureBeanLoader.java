/*
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
package org.apache.camel.dsl.support;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.TypeConverters;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.spi.BacklogTracer;
import org.apache.camel.spi.BeanLoader;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.CliConnectorFactory;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.EndpointStrategy;
import org.apache.camel.spi.EventFactory;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ExchangeFactory;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.FactoryFinderResolver;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.spi.LogListener;
import org.apache.camel.spi.ManagementObjectNameStrategy;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.NodeIdFactory;
import org.apache.camel.spi.ProcessorFactory;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.RoutePolicyFactory;
import org.apache.camel.spi.RuntimeEndpointRegistry;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.apache.camel.spi.UuidGenerator;

/**
 * This {@link BeanLoader} is used by YAML and XML DSL when they have beans in their DSLs, which are beans for advanced
 * auto configuration of {@link CamelContext}.
 */
public class AutoConfigureBeanLoader implements BeanLoader, CamelContextAware {

    private CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void onLoadedBean(String name, Object bean) {
        // similar logic (almost) to DefaultConfigurationConfigurer.afterConfigure

        ExtendedCamelContext ecc = camelContext.getCamelContextExtension();
        ManagementStrategy managementStrategy = camelContext.getManagementStrategy();

        if (bean instanceof StartupStepRecorder ssr) {
            ecc.setStartupStepRecorder(ssr);
        } else if (bean instanceof CliConnectorFactory ccf) {
            ecc.addContextPlugin(CliConnectorFactory.class, ccf);
        } else if (bean instanceof PropertiesComponent pc) {
            camelContext.setPropertiesComponent(pc);
        } else if (bean instanceof BacklogTracer bt) {
            ecc.addContextPlugin(BacklogTracer.class, bt);
        } else if (bean instanceof InflightRepository ifr) {
            camelContext.setInflightRepository(ifr);
        } else if (bean instanceof AsyncProcessorAwaitManager am) {
            ecc.addContextPlugin(AsyncProcessorAwaitManager.class, am);
        } else if (bean instanceof ManagementStrategy ms) {
            camelContext.setManagementStrategy(ms);
        } else if (bean instanceof ManagementObjectNameStrategy mos) {
            managementStrategy.setManagementObjectNameStrategy(mos);
        } else if (bean instanceof EventFactory ef) {
            managementStrategy.setEventFactory(ef);
        } else if (bean instanceof UnitOfWorkFactory uowf) {
            ecc.addContextPlugin(UnitOfWorkFactory.class, uowf);
        } else if (bean instanceof RuntimeEndpointRegistry rer) {
            camelContext.setRuntimeEndpointRegistry(rer);
        } else if (bean instanceof ModelJAXBContextFactory jf) {
            ecc.addContextPlugin(ModelJAXBContextFactory.class, jf);
        } else if (bean instanceof ClassResolver cr) {
            camelContext.setClassResolver(cr);
        } else if (bean instanceof FactoryFinderResolver ffr) {
            ecc.addContextPlugin(FactoryFinderResolver.class, ffr);
        } else if (bean instanceof RouteController rc) {
            camelContext.setRouteController(rc);
        } else if (bean instanceof UuidGenerator ug) {
            camelContext.setUuidGenerator(ug);
        } else if (bean instanceof ExecutorServiceManager em) {
            camelContext.setExecutorServiceManager(em);
        } else if (bean instanceof ThreadPoolFactory tpf) {
            camelContext.getExecutorServiceManager().setThreadPoolFactory(tpf);
        } else if (bean instanceof ProcessorFactory pf) {
            ecc.addContextPlugin(ProcessorFactory.class, pf);
        } else if (bean instanceof Debugger deb) {
            camelContext.setDebugger(deb);
        } else if (bean instanceof NodeIdFactory nf) {
            ecc.addContextPlugin(NodeIdFactory.class, nf);
        } else if (bean instanceof MessageHistoryFactory mf) {
            camelContext.setMessageHistoryFactory(mf);
        } else if (bean instanceof ReactiveExecutor re) {
            ecc.setReactiveExecutor(re);
        } else if (bean instanceof ShutdownStrategy ss) {
            camelContext.setShutdownStrategy(ss);
        } else if (bean instanceof ExchangeFactory ef) {
            ecc.setExchangeFactory(ef);
        } else if (bean instanceof TypeConverters tc) {
            camelContext.getTypeConverterRegistry().addTypeConverters(tc);
        } else if (bean instanceof EventNotifier en) {
            managementStrategy.addEventNotifier(en);
        } else if (bean instanceof EndpointStrategy es) {
            ecc.registerEndpointCallback(es);
        } else if (bean instanceof RoutePolicyFactory rpf) {
            camelContext.addRoutePolicyFactory(rpf);
        } else if (bean instanceof LogListener ll) {
            boolean contains = ecc.getLogListeners() != null
                    && ecc.getLogListeners().contains(ll);
            if (!contains) {
                ecc.addLogListener(ll);
            }
        }
    }

}
