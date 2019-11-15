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
package org.apache.camel.component.kubernetes.cloud;

import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.naming.NamingEnumeration;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.kubernetes.KubernetesConfiguration;
import org.apache.camel.impl.cloud.DefaultServiceDefinition;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers where services are running on which servers in Kubernetes.
 */
public class KubernetesDnsSrvServiceDiscovery extends KubernetesServiceDiscovery {
    private static final Logger LOGGER;
    private static final String[] ATTRIBUTE_IDS;
    private static final Hashtable<String, String> ENV;

    static {
        LOGGER = LoggerFactory.getLogger(KubernetesDnsSrvServiceDiscovery.class);
        ATTRIBUTE_IDS = new String[] {"SRV"};

        ENV = new Hashtable<>();
        ENV.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        ENV.put("java.naming.provider.url", "dns:");
    }

    private final String namespace;
    private final String portName;
    private final String portProtocol;
    private final String zone;
    private final Map<String, String> cache;

    public KubernetesDnsSrvServiceDiscovery(KubernetesConfiguration configuration) {
        super(configuration);

        this.namespace = configuration.getNamespace() != null ? configuration.getNamespace() : System.getenv("KUBERNETES_NAMESPACE");
        this.portName = configuration.getPortName();
        this.portProtocol = configuration.getPortProtocol();
        this.zone = configuration.getDnsDomain();

        // validation
        ObjectHelper.notNull(namespace, "Namespace");
        ObjectHelper.notNull(portName, "Port Name");
        ObjectHelper.notNull(portProtocol, "Port Protocol");
        ObjectHelper.notNull(zone, "DNS Domain");

        this.cache = new HashMap<>();
    }

    /**
     * Compute the query string to lookup SRV records.
     * https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/#supported-dns-schema
     * https://github.com/kubernetes/dns/blob/master/docs/specification.md
     *
     * @param serviceName the service name
     * @return the query
     */
    protected String computeQueryString(String serviceName) {
        // _<port_name>._<port_proto>.<serviceName>.<namespace>.svc.<zone>.
        return String.format("_%s._%s.%s.%s.svc.%s", this.portName, this.portProtocol, serviceName, this.namespace, this.zone);
    }

    @Override
    public List<ServiceDefinition> getServices(String name) {
        try {
            final String query = cache.computeIfAbsent(name, this::computeQueryString);
            final DirContext ctx = new InitialDirContext(ENV);
            final NamingEnumeration<?> resolved = ctx.getAttributes(query, ATTRIBUTE_IDS).get("srv").getAll();

            if (resolved.hasMore()) {
                List<ServiceDefinition> servers = new LinkedList<>();

                while (resolved.hasMore()) {
                    String record = (String)resolved.next();
                    String[] items = record.split(" ", -1);
                    String host = items[3].trim();
                    String port = items[2].trim();

                    if (ObjectHelper.isEmpty(host) || ObjectHelper.isEmpty(port)) {
                        continue;
                    }

                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Found service address {}:{} for query: {}", host, port, query);
                    }

                    if (!"0".equals(port)) {
                        servers.add(new DefaultServiceDefinition(name, host, Integer.parseInt(port)));
                    }
                }

                return servers;
            } else {
                LOGGER.warn("Could not find any service for name={}, query={}", name, query);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not resolve services via DNSSRV", e);
        }

        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return "KubernetesDnsSrvServiceDiscovery{" + "namespace='" + namespace + '\'' + ", portName='" + portName + '\'' + ", portProtocol='" + portProtocol + '\'' + ", zone='"
               + zone + '\'' + '}';
    }
}
