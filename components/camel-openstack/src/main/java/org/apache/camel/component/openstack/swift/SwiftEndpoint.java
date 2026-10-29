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
package org.apache.camel.component.openstack.swift;

import org.apache.camel.Category;
import org.apache.camel.Producer;
import org.apache.camel.component.openstack.common.AbstractOpenstackEndpoint;
import org.apache.camel.component.openstack.swift.producer.ContainerProducer;
import org.apache.camel.component.openstack.swift.producer.ObjectProducer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.openstack4j.core.transport.Config;

import static org.apache.camel.component.openstack.common.OpenstackConstants.SCHEME_SWIFT;

/**
 * Access OpenStack Swift object/blob store.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = SCHEME_SWIFT, title = "OpenStack Swift", syntax = "openstack-swift:host",
             category = { Category.CONTAINER }, producerOnly = true, headersClass = SwiftConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "For create of an object the content: an InputStream, a File, a URL or a Payload of openstack4j; "
                                                + "text, bytes or the file of the file consumer fail: convert it to a File or an InputStream first. "
                                                + "createUpdateMetadata and deleteMetadata take the metadata as a Map. The containers take a "
                                                + "CreateUpdateContainerOptions or ContainerListOptions of openstack4j, or else headers"),
           producerOut = @BodyType(value = {
                   Carrier.TEXT, Carrier.POJO, Carrier.LIST, Carrier.MAP, Carrier.NULL, Carrier.UNCHANGED },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "getAll", carriers = Carrier.LIST),
                                           @BodyType.When(option = "operation", value = "getMetadata", carriers = Carrier.MAP),
                                           @BodyType.When(option = "operation", value = "delete", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "createUpdateMetadata",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "deleteMetadata",
                                                          carriers = Carrier.UNCHANGED) },
                                   description = "For objects: the ETag as text after create, the SwiftObject of openstack4j for get (its "
                                                 + "download() gives the content), a List of them for getAll. For containers: a List of "
                                                 + "SwiftContainer for get and getAll; create and update keep the body. getMetadata gives a Map of "
                                                 + "the metadata; delete and the metadata updates keep the body"))
public class SwiftEndpoint extends AbstractOpenstackEndpoint {

    @UriParam(enums = "objects,containers")
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
    @Metadata(required = true, security = "secret")
    private String username;

    @UriParam
    @Metadata(required = true, security = "secret")
    private String password;

    @UriParam
    private Config config;

    @UriParam(defaultValue = V3, enums = "V2,V3")
    private String apiVersion = V3;

    public SwiftEndpoint(String uri, SwiftComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        switch (subsystem) {
            case SwiftConstants.SWIFT_SUBSYSTEM_OBJECTS:
                return new ObjectProducer(this, createClient());
            case SwiftConstants.SWIFT_SUBSYSTEM_CONTAINERS:
                return new ContainerProducer(this, createClient());
            default:
                throw new IllegalArgumentException("Can't create producer with subsystem " + subsystem);
        }
    }

    public String getSubsystem() {
        return subsystem;
    }

    /**
     * OpenStack Swift subsystem
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
     * OpenStack configuration
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
