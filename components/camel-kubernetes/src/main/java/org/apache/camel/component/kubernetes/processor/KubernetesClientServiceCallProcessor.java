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

import java.util.Collection;
import java.util.concurrent.RejectedExecutionException;

import io.fabric8.kubernetes.client.AutoAdaptableKubernetesClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Traceable;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.component.kubernetes.KubernetesConstants;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.ServiceCallLoadBalancer;
import org.apache.camel.spi.ServiceCallServerListStrategy;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kubernetes based implementation of the the ServiceCall EIP where the service lookup is client based.
 */
public class KubernetesClientServiceCallProcessor extends ServiceSupport implements AsyncProcessor, CamelContextAware, Traceable, IdAware {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesClientServiceCallProcessor.class);

    private CamelContext camelContext;
    private String id;
    private final String name;
    private final String scheme;
    private final String contextPath;
    private final String namespace;
    private final String uri;
    private final ExchangePattern exchangePattern;
    private final KubernetesConfiguration configuration;
    private final KubernetesServiceCallExpression serviceCallExpression;
    private ServiceCallServerListStrategy<KubernetesServer> serverListStrategy;
    private ServiceCallLoadBalancer<KubernetesServer> loadBalancer;
    private SendDynamicProcessor processor;

    public KubernetesClientServiceCallProcessor(String name, String namespace, String scheme, String uri, ExchangePattern exchangePattern, KubernetesConfiguration configuration) {
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

        // if no namespace configured then resolve from environment variables
        if (namespace == null) {
            this.namespace = System.getenv("KUBERNETES_NAMESPACE");
        } else {
            this.namespace = namespace;
        }
        this.uri = uri;
        this.exchangePattern = exchangePattern;
        this.configuration = configuration;
        this.serviceCallExpression = new KubernetesServiceCallExpression(this.name, this.scheme, this.contextPath, this.uri);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Collection<KubernetesServer> servers = null;
        try {
            servers = serverListStrategy.getUpdatedListOfServers();
            if (servers == null || servers.isEmpty()) {
                exchange.setException(new RejectedExecutionException("No active services with name " + name + " in namespace " + namespace));
            }
        } catch (Throwable e) {
            exchange.setException(e);
        }

        if (exchange.getException() != null) {
            callback.done(true);
            return true;
        }

        // let the client load balancer chose which server to use
        KubernetesServer server = loadBalancer.chooseServer(servers);
        String ip = server.getIp();
        int port = server.getPort();
        LOG.debug("Service {} active at server: {}:{}", name, ip, port);

        // set selected server as header
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVER_IP, ip);
        exchange.getIn().setHeader(KubernetesConstants.KUBERNETES_SERVER_PORT, port);

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

    public ServiceCallLoadBalancer<KubernetesServer> getLoadBalancer() {
        return loadBalancer;
    }

    public void setLoadBalancer(ServiceCallLoadBalancer<KubernetesServer> loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    public ServiceCallServerListStrategy getServerListStrategy() {
        return serverListStrategy;
    }

    public void setServerListStrategy(ServiceCallServerListStrategy serverListStrategy) {
        this.serverListStrategy = serverListStrategy;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notEmpty(name, "name", this);
        ObjectHelper.notEmpty(namespace, "namespace", this);
        ObjectHelper.notEmpty(configuration.getMasterUrl(), "masterUrl", this);

        if (loadBalancer == null) {
            loadBalancer = new RandomLoadBalancer();
        }
        if (serverListStrategy == null) {
            serverListStrategy = new KubernetesServiceCallServerListStrategy(name, namespace, null, createKubernetesClient());
        }
        LOG.info("KubernetesServiceCall at namespace: {} with service name: {} is using load balancer: {} and service discovery: {}", namespace, name, loadBalancer, serverListStrategy);

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

    private AutoAdaptableKubernetesClient createKubernetesClient() {
        LOG.debug("Create Kubernetes client with the following Configuration: " + configuration.toString());

        ConfigBuilder builder = new ConfigBuilder();
        builder.withMasterUrl(configuration.getMasterUrl());
        if ((ObjectHelper.isNotEmpty(configuration.getUsername())
                && ObjectHelper.isNotEmpty(configuration.getPassword()))
                && ObjectHelper.isEmpty(configuration.getOauthToken())) {
            builder.withUsername(configuration.getUsername());
            builder.withPassword(configuration.getPassword());
        } else {
            builder.withOauthToken(configuration.getOauthToken());
        }
        if (ObjectHelper.isNotEmpty(configuration.getCaCertData())) {
            builder.withCaCertData(configuration.getCaCertData());
        }
        if (ObjectHelper.isNotEmpty(configuration.getCaCertFile())) {
            builder.withCaCertFile(configuration.getCaCertFile());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientCertData())) {
            builder.withClientCertData(configuration.getClientCertData());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientCertFile())) {
            builder.withClientCertFile(configuration.getClientCertFile());
        }
        if (ObjectHelper.isNotEmpty(configuration.getApiVersion())) {
            builder.withApiVersion(configuration.getApiVersion());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientKeyAlgo())) {
            builder.withClientKeyAlgo(configuration.getClientKeyAlgo());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientKeyData())) {
            builder.withClientKeyData(configuration.getClientKeyData());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientKeyFile())) {
            builder.withClientKeyFile(configuration.getClientKeyFile());
        }
        if (ObjectHelper.isNotEmpty(configuration.getClientKeyPassphrase())) {
            builder.withClientKeyPassphrase(configuration.getClientKeyPassphrase());
        }
        if (ObjectHelper.isNotEmpty(configuration.getTrustCerts())) {
            builder.withTrustCerts(configuration.getTrustCerts());
        }

        Config conf = builder.build();
        return new AutoAdaptableKubernetesClient(conf);
    }

}
