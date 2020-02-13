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
package org.apache.camel.component.openstack.neutron;

import org.apache.camel.Producer;
import org.apache.camel.component.openstack.common.AbstractOpenstackEndpoint;
import org.apache.camel.component.openstack.neutron.producer.NetworkProducer;
import org.apache.camel.component.openstack.neutron.producer.PortProducer;
import org.apache.camel.component.openstack.neutron.producer.RouterProducer;
import org.apache.camel.component.openstack.neutron.producer.SubnetProducer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.openstack4j.core.transport.Config;

/**
 * The openstack-neutron component allows messages to be sent to an OpenStack network services.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "openstack-neutron", title = "OpenStack Neutron", syntax = "openstack-neutron:host", label = "cloud,paas", producerOnly = true)
public class NeutronEndpoint extends AbstractOpenstackEndpoint {

    @UriParam(enums = "networks,subnets,ports,routers")
    @Metadata(required = true)
    String subsystem;
    @UriPath
    @Metadata(required = true)
    private String host;
    @UriParam(defaultValue = "default")
    private String domain = "default";

    @UriParam
    @Metadata(required = true)
    private String project;

    @UriParam
    private String operation;

    @UriParam
    @Metadata(required = true, secret = true)
    private String username;

    @UriParam
    @Metadata(required = true, secret = true)
    private String password;

    @UriParam
    private Config config;

    @UriParam(defaultValue = V3, enums = "V2,V3")
    private String apiVersion = V3;

    public NeutronEndpoint(String uri, NeutronComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        switch (getSubsystem()) {
            case NeutronConstants.NEUTRON_NETWORK_SUBSYSTEM:
                return new NetworkProducer(this, createClient());
            case NeutronConstants.NEUTRON_SUBNETS_SYSTEM:
                return new SubnetProducer(this, createClient());
            case NeutronConstants.NEUTRON_PORT_SYSTEM:
                return new PortProducer(this, createClient());
            case NeutronConstants.NEUTRON_ROUTER_SYSTEM:
                return new RouterProducer(this, createClient());
            default:
                throw new IllegalArgumentException("Can't create producer with subsystem " + subsystem);
        }
    }

    public String getSubsystem() {
        return subsystem;
    }

    /**
     * OpenStack Neutron subsystem
     */
    public void setSubsystem(String subsystem) {
        this.subsystem = subsystem;
    }

    @Override
    public String getDomain() {
        return domain;
    }

    /**
     * Authentication domain
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    @Override
    public String getProject() {
        return project;
    }

    /**
     * The project ID
     */
    public void setProject(String project) {
        this.project = project;
    }

    @Override
    public String getOperation() {
        return operation;
    }

    /**
     * The operation to do
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    @Override
    public String getUsername() {
        return username;
    }

    /**
     * OpenStack username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    /**
     * OpenStack password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String getHost() {
        return host;
    }

    /**
     * OpenStack host url
     */
    public void setHost(String host) {
        this.host = host;
    }

    @Override
    public Config getConfig() {
        return config;
    }

    /**
     *OpenStack configuration
     */
    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public String getApiVersion() {
        return apiVersion;
    }

    /**
     * OpenStack API version
     */
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }
}
