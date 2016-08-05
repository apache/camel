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
package org.apache.camel.component.ribbon.processor;

import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.IRule;
import com.netflix.loadbalancer.PollingServerListUpdater;
import com.netflix.loadbalancer.RoundRobinRule;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.netflix.loadbalancer.ServerListUpdater;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Traceable;
import org.apache.camel.component.ribbon.RibbonConfiguration;
import org.apache.camel.impl.remote.DefaultServiceCallExpression;
import org.apache.camel.impl.remote.ServiceCallConstants;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.ServiceCallServerListStrategy;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ribbon based implementation of the the ServiceCall EIP.
 */
public class RibbonServiceCallProcessor extends ServiceSupport implements AsyncProcessor, CamelContextAware, Traceable, IdAware {

    private static final Logger LOG = LoggerFactory.getLogger(RibbonServiceCallProcessor.class);

    private CamelContext camelContext;
    private String id;
    private final String name;
    private final String scheme;
    private final String contextPath;
    private final String uri;
    private final ExchangePattern exchangePattern;
    private final RibbonConfiguration configuration;
    private ServiceCallServerListStrategy<RibbonServer> serverListStrategy;
    private ZoneAwareLoadBalancer<RibbonServer> ribbonLoadBalancer;
    private IRule rule;
    private IPing ping;
    private final DefaultServiceCallExpression serviceCallExpression;
    private Map<String, String> ribbonClientConfig;
    private SendDynamicProcessor processor;

    public RibbonServiceCallProcessor(String name, String uri, String scheme, ExchangePattern exchangePattern, RibbonConfiguration configuration) {
        // setup from the provided name which can contain scheme and context-path information as well
        String serviceName;
        if (name.contains("/")) {
            serviceName = ObjectHelper.before(name, "/");
            this.contextPath = ObjectHelper.after(name, "/");
        } else if (name.contains("?")) {
            serviceName = ObjectHelper.before(name, "?");
            this.contextPath = ObjectHelper.after(name, "?");
        } else {
            serviceName = name;
            this.contextPath = null;
        }
        if (serviceName.contains(":")) {
            this.scheme = ObjectHelper.before(serviceName, ":");
            this.name = ObjectHelper.after(serviceName, ":");
        } else {
            this.scheme = scheme;
            this.name = serviceName;
        }

        this.uri = uri;
        this.exchangePattern = exchangePattern;
        this.configuration = configuration;
        this.rule = configuration.getRule();
        this.ping = configuration.getPing();
        this.serviceCallExpression = new DefaultServiceCallExpression(this.name, this.scheme, this.contextPath, this.uri);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Server server = null;
        try {
            // let the client load balancer chose which server to use
            server = ribbonLoadBalancer.chooseServer();
            if (server == null) {
                exchange.setException(new RejectedExecutionException("No active services with name " + name));
            }
        } catch (Throwable e) {
            exchange.setException(e);
        }

        if (exchange.getException() != null) {
            callback.done(true);
            return true;
        }

        String ip = server.getHost();
        int port = server.getPort();
        LOG.debug("Service {} active at server: {}:{}", name, ip, port);

        // set selected server as header
        exchange.getIn().setHeader(ServiceCallConstants.SERVER_IP, ip);
        exchange.getIn().setHeader(ServiceCallConstants.SERVER_PORT, port);
        exchange.getIn().setHeader(ServiceCallConstants.SERVICE_NAME, name);

        // use the dynamic send processor to call the service
        return processor.process(exchange, callback);
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getTraceLabel() {
        return "kubernetes";
    }

    public ServiceCallServerListStrategy getServerListStrategy() {
        return serverListStrategy;
    }

    public void setServerListStrategy(ServiceCallServerListStrategy serverListStrategy) {
        this.serverListStrategy = serverListStrategy;
    }

    public IRule getRule() {
        return rule;
    }

    public void setRule(IRule rule) {
        this.rule = rule;
    }

    public IPing getPing() {
        return ping;
    }

    public void setPing(IPing ping) {
        this.ping = ping;
    }

    public Map<String, String> getRibbonClientConfig() {
        return ribbonClientConfig;
    }

    public void setRibbonClientConfig(Map<String, String> ribbonClientConfig) {
        this.ribbonClientConfig = ribbonClientConfig;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doStart() throws Exception {
        ObjectHelper.notEmpty(name, "name", this);

        if (serverListStrategy == null) {
            serverListStrategy = new RibbonServiceCallStaticServerListStrategy();
        }

        if (!(serverListStrategy instanceof ServerList)) {
            throw new IllegalArgumentException("ServerListStrategy must be instanceof com.netflix.loadbalancer.ServerList but is of type: " + serverListStrategy.getClass().getName());
        }

        if (rule == null) {
            // use round robin rule by default
            rule = new RoundRobinRule();
        }
        if (ping == null) {
            // use dummy ping by default
            ping = new DummyPing();
        }

        // setup client config
        IClientConfig config = IClientConfig.Builder.newBuilder(name).build();
        if (ribbonClientConfig != null) {
            for (Map.Entry<String, String> entry : ribbonClientConfig.entrySet()) {
                IClientConfigKey key = IClientConfigKey.Keys.valueOf(entry.getKey());
                String value = entry.getValue();
                LOG.debug("RibbonClientConfig: {}={}", key.key(), value);
                config.set(key, entry.getValue());
            }
        }

        ServerListUpdater updater = new PollingServerListUpdater(config);
        ribbonLoadBalancer = new ZoneAwareLoadBalancer<>(config, rule, ping, (ServerList<RibbonServer>) serverListStrategy, null, updater);

        LOG.info("RibbonServiceCall with service name: {} is using load balancer: {} and server list: {}", name, ribbonLoadBalancer, serverListStrategy);

        processor = new SendDynamicProcessor(uri, serviceCallExpression);
        processor.setCamelContext(getCamelContext());
        if (exchangePattern != null) {
            processor.setPattern(exchangePattern);
        }
        ServiceHelper.startServices(serverListStrategy, processor);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(processor, serverListStrategy);
    }

}

