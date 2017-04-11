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
package org.apache.camel.component.netty4;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadFactory;

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.concurrent.CamelThreadFactory;

public class NettyComponent extends UriEndpointComponent implements SSLContextParametersAware {

    @Metadata(label = "advanced")
    private NettyConfiguration configuration;
    @Metadata(label = "advanced", defaultValue = "16")
    private int maximumPoolSize = 16;
    @Metadata(label = "advanced")
    private volatile EventExecutorGroup executorService;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public NettyComponent() {
        super(NettyEndpoint.class);
    }

    public NettyComponent(Class<? extends Endpoint> endpointClass) {
        super(endpointClass);
    }

    public NettyComponent(CamelContext context) {
        super(context, NettyEndpoint.class);
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * The thread pool size for the EventExecutorGroup if its in use.
     * <p/>
     * The default value is 16.
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        NettyConfiguration config;
        if (configuration != null) {
            config = configuration.copy();
        } else {
            config = new NettyConfiguration();
        }
        config = parseConfiguration(config, remaining, parameters);

        // merge any custom bootstrap configuration on the config
        NettyServerBootstrapConfiguration bootstrapConfiguration = resolveAndRemoveReferenceParameter(parameters, "bootstrapConfiguration", NettyServerBootstrapConfiguration.class);
        if (bootstrapConfiguration != null) {
            Map<String, Object> options = new HashMap<String, Object>();
            if (IntrospectionSupport.getProperties(bootstrapConfiguration, options, null, false)) {
                IntrospectionSupport.setProperties(getCamelContext().getTypeConverter(), config, options);
            }
        }

        if (config.getSslContextParameters() == null) {
            config.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        // validate config
        config.validateConfiguration();

        NettyEndpoint nettyEndpoint = new NettyEndpoint(remaining, this, config);
        setProperties(nettyEndpoint.getConfiguration(), parameters);
        return nettyEndpoint;
    }

    /**
     * Parses the configuration
     *
     * @return the parsed and valid configuration to use
     */
    protected NettyConfiguration parseConfiguration(NettyConfiguration configuration, String remaining, Map<String, Object> parameters) throws Exception {
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
     * To use the given EventExecutorGroup
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
        if (configuration == null) {
            configuration = new NettyConfiguration();
        }

        //Only setup the executorService if it is needed
        if (configuration.isUsingExecutorService() && executorService == null) {
            executorService = createExecutorService();
        }

        super.doStart();
    }

    protected EventExecutorGroup createExecutorService() {
        // Provide the executor service for the application 
        // and use a Camel thread factory so we have consistent thread namings
        // we should use a shared thread pool as recommended by Netty
        String pattern = getCamelContext().getExecutorServiceManager().getThreadNamePattern();
        ThreadFactory factory = new CamelThreadFactory(pattern, "NettyEventExecutorGroup", true);
        return new DefaultEventExecutorGroup(getMaximumPoolSize(), factory);
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
