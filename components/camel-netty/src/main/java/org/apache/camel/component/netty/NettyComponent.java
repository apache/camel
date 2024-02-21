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
package org.apache.camel.component.netty;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.internal.SystemPropertyUtil;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("netty")
public class NettyComponent extends DefaultComponent implements SSLContextParametersAware {

    private static final Logger LOG = LoggerFactory.getLogger(NettyComponent.class);

    @Metadata
    private NettyConfiguration configuration = new NettyConfiguration();
    @Metadata(label = "consumer,advanced")
    private int maximumPoolSize;
    @Metadata(label = "consumer,advanced")
    private volatile EventExecutorGroup executorService;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public NettyComponent() {
    }

    public NettyComponent(Class<? extends Endpoint> endpointClass) {
    }

    public NettyComponent(CamelContext context) {
        super(context);
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * Sets a maximum thread pool size for the netty consumer ordered thread pool. The default size is 2 x cpu_core plus
     * 1. Setting this value to eg 10 will then use 10 threads unless 2 x cpu_core plus 1 is a higher value, which then
     * will override and be used. For example if there are 8 cores, then the consumer thread pool will be 17.
     *
     * This thread pool is used to route messages received from Netty by Camel. We use a separate thread pool to ensure
     * ordering of messages and also in case some messages will block, then nettys worker threads (event loop) wont be
     * affected.
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        NettyConfiguration config = configuration.copy();
        config = parseConfiguration(config, remaining, parameters);

        // merge any custom bootstrap configuration on the config
        NettyServerBootstrapConfiguration bootstrapConfiguration = resolveAndRemoveReferenceParameter(parameters,
                "bootstrapConfiguration", NettyServerBootstrapConfiguration.class);
        if (bootstrapConfiguration != null) {
            Map<String, Object> options = new HashMap<>();
            BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(getCamelContext());
            if (beanIntrospection.getProperties(bootstrapConfiguration, options, null, false)) {
                PropertyBindingSupport.bindProperties(getCamelContext(), config, options);
            }
        }

        if (config.getSslContextParameters() == null) {
            config.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        // validate config
        config.validateConfiguration();

        NettyEndpoint nettyEndpoint = new NettyEndpoint(uri, this, config);
        setProperties(nettyEndpoint, parameters);
        return nettyEndpoint;
    }

    /**
     * Parses the configuration
     *
     * @return the parsed and valid configuration to use
     */
    protected NettyConfiguration parseConfiguration(
            NettyConfiguration configuration, String remaining, Map<String, Object> parameters)
            throws Exception {
        configuration.parseURI(new URI(remaining), parameters, this, "tcp", "udp");
        return configuration;
    }

    public NettyConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use the NettyConfiguration as configuration when creating endpoints.
     */
    public void setConfiguration(NettyConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * To use the given EventExecutorGroup.
     */
    public void setExecutorService(EventExecutorGroup executorService) {
        this.executorService = executorService;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

    public EventExecutorGroup getExecutorService() {
        return executorService;
    }

    @Override
    protected void doStart() throws Exception {
        //Only setup the executorService if it is needed
        if (configuration.isUsingExecutorService() && executorService == null) {
            int netty = SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2);
            // we want one more thread than netty uses for its event loop
            // and if there is a custom size for maximum pool size then use it, unless netty event loops has more threads
            // and therefore we use math.max to find the highest value
            int threads = Math.max(maximumPoolSize, netty + 1);
            executorService = NettyHelper.createExecutorGroup(getCamelContext(), "NettyConsumerExecutorGroup", threads);
            LOG.info("Creating shared NettyConsumerExecutorGroup with {} threads", threads);
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        //Only shutdown the executorService if it is created by netty component
        if (configuration.isUsingExecutorService() && executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);
            executorService = null;
        }

        //shutdown workerPool if configured
        if (configuration.getWorkerGroup() != null) {
            configuration.getWorkerGroup().shutdownGracefully();
        }

        super.doStop();
    }

}
