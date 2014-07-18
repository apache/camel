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
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.concurrent.CamelThreadFactory;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

public class NettyComponent extends UriEndpointComponent {
    // use a shared timer for Netty (see javadoc for HashedWheelTimer)
    private static volatile Timer timer;
    private NettyConfiguration configuration;
    private OrderedMemoryAwareThreadPoolExecutor executorService;

    public NettyComponent() {
        super(NettyEndpoint.class);
    }

    public NettyComponent(Class<? extends Endpoint> endpointClass) {
        super(endpointClass);
    }

    public NettyComponent(CamelContext context) {
        super(context, NettyEndpoint.class);
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

        // validate config
        config.validateConfiguration();

        NettyEndpoint nettyEndpoint = new NettyEndpoint(remaining, this, config);
        nettyEndpoint.setTimer(getTimer());
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

    public void setConfiguration(NettyConfiguration configuration) {
        this.configuration = configuration;
    }

    public static Timer getTimer() {
        return timer;
    }

    public synchronized OrderedMemoryAwareThreadPoolExecutor getExecutorService() {
        if (executorService == null) {
            executorService = createExecutorService();
        }
        return executorService;
    }

    @Override
    protected void doStart() throws Exception {
        if (timer == null) {
            timer = new HashedWheelTimer();
        }

        if (configuration == null) {
            configuration = new NettyConfiguration();
        }
        if (configuration.isOrderedThreadPoolExecutor()) {
            executorService = createExecutorService();
        }

        super.doStart();
    }

    protected OrderedMemoryAwareThreadPoolExecutor createExecutorService() {
        // use ordered thread pool, to ensure we process the events in order, and can send back
        // replies in the expected order. eg this is required by TCP.
        // and use a Camel thread factory so we have consistent thread namings
        // we should use a shared thread pool as recommended by Netty
        String pattern = getCamelContext().getExecutorServiceManager().getThreadNamePattern();
        ThreadFactory factory = new CamelThreadFactory(pattern, "NettyOrderedWorker", true);
        return new OrderedMemoryAwareThreadPoolExecutor(configuration.getMaximumPoolSize(),
                0L, 0L, 30, TimeUnit.SECONDS, factory);
    }

    @Override
    protected void doStop() throws Exception {
        timer.stop();
        timer = null;

        if (executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
            executorService = null;
        }

        super.doStop();
    }

}