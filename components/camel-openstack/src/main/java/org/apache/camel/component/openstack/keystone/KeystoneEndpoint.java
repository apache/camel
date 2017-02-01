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
package org.apache.camel.component.openstack.keystone;

import org.apache.camel.Producer;
import org.apache.camel.component.openstack.common.AbstractOpenstackEndpoint;
import org.apache.camel.component.openstack.keystone.producer.DomainProducer;
import org.apache.camel.component.openstack.keystone.producer.GroupProducer;
import org.apache.camel.component.openstack.keystone.producer.ProjectProducer;
import org.apache.camel.component.openstack.keystone.producer.RegionProducer;
import org.apache.camel.component.openstack.keystone.producer.UserProducer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.openstack4j.core.transport.Config;

/**
 * The openstack-keystone component allows messages to be sent to an OpenStack identity services.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "openstack-keystone", title = "OpenStack Keystone", syntax = "openstack-keystone:host", label = "cloud,paas", producerOnly = true)
public class KeystoneEndpoint extends AbstractOpenstackEndpoint {

    @UriParam(enums = "regions,domains,projects,users,groups")
    @Metadata(required = "true")
    String subsystem;
    @UriPath
    @Metadata(required = "true")
    private String host;
    @UriParam(defaultValue = "default")
    private String domain = "default";

    @UriParam
    @Metadata(required = "true")
    private String project;

    @UriParam
    private String operation;

    @UriParam
    @Metadata(required = "true", secret = true)
    private String username;

    @UriParam
    @Metadata(required = "true", secret = true)
    private String password;

    @UriParam
    private Config config;

    public KeystoneEndpoint(String uri, KeystoneComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        switch (getSubsystem()) {
        case KeystoneConstants.REGIONS:
            return new RegionProducer(this, createClient());
        case KeystoneConstants.DOMAINS:
            return new DomainProducer(this, createClient());
        case KeystoneConstants.PROJECTS:
            return new ProjectProducer(this, createClient());
        case KeystoneConstants.USERS:
            return new UserProducer(this, createClient());
        case KeystoneConstants.GROUPS:
            return new GroupProducer(this, createClient());
        default:
            throw new IllegalArgumentException("Can't create producer with subsystem " + subsystem);
        }
    }

    public String getSubsystem() {
        return subsystem;
    }

    /**
     * OpenStack Keystone subsystem
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

    public Config getConfig() {
        return config;
    }

    /**
     *OpenStack configuration
     */
    public void setConfig(Config config) {
        this.config = config;
    }

    // V2 API is not supported (is deprecated)
    public String getApiVersion() {
        return V3;
    }
}
