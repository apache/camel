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
package org.apache.camel.component.openshift;

import com.openshift.client.IApplication;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

/**
 * To manage your Openshift 2.x applications.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "openshift", title = "OpenShift", syntax = "openshift:clientId", consumerClass = OpenShiftConsumer.class, label = "cloud,paas")
public class OpenShiftEndpoint extends ScheduledPollEndpoint {

    @UriPath @Metadata(required = "true")
    private String clientId;
    @UriParam @Metadata(required = "true", secret = true)
    private String username;
    @UriParam @Metadata(required = "true", secret = true)
    private String password;
    @UriParam
    private String domain;
    @UriParam
    private String server;
    @UriParam(label = "producer", enums = "list,start,stop,restart,state,getStandaloneCartridge,getEmbeddedCartridges,addEmbeddedCartridge,removeEmbeddedCartridge,"
            + "scaleUp,scaleDown,getGitUrl,getDeploymentType,setDeploymentType,getAllEnvironmentVariables,addEnvironmentVariable,addMultipleEnvironmentVariables,"
            + "updateEnvironmentVariable,getEnvironmentVariableValue,removeEnvironmentVariable,getGearProfile,addAlias,removeAlias,getAliases")
    private String operation;
    @UriParam(label = "producer")
    private String application;
    @UriParam(label = "producer", enums = "pojo,json")
    private String mode;

    public OpenShiftEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        ObjectHelper.notEmpty(clientId, "clientId", this);
        ObjectHelper.notEmpty(username, "username", this);
        ObjectHelper.notEmpty(password, "password", this);

        return new OpenShiftProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notEmpty(clientId, "clientId", this);
        ObjectHelper.notEmpty(username, "username", this);
        ObjectHelper.notEmpty(password, "password", this);

        Consumer consumer = new OpenShiftConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public Exchange createExchange(IApplication application) {
        Exchange exchange = super.createExchange();
        exchange.getIn().setBody(application);
        return exchange;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public String getUsername() {
        return username;
    }

    /**
     * The username to login to openshift server.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * The password for login to openshift server.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * The client id
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getDomain() {
        return domain;
    }

    /**
     * Domain name. If not specified then the default domain is used.
     */
    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getServer() {
        return server;
    }

    /**
     * Url to the openshift server.
     * If not specified then the default value from the local openshift configuration file ~/.openshift/express.conf is used.
     * And if that fails as well then "openshift.redhat.com" is used.
     */
    public void setServer(String server) {
        this.server = server;
    }

    public String getOperation() {
        return operation;
    }

    /**
     * The operation to perform which can be: list, start, stop, restart, and state.
     * The list operation returns information about all the applications in json format.
     * The state operation returns the state such as: started, stopped etc.
     * The other operations does not return any value.
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public void setOperation(OpenShiftOperation operation) {
        this.operation = operation.name();
    }

    public String getApplication() {
        return application;
    }

    /**
     * The application name to start, stop, restart, or get the state.
     */
    public void setApplication(String application) {
        this.application = application;
    }

    public String getMode() {
        return mode;
    }

    /**
     * Whether to output the message body as a pojo or json. For pojo the message is a List<com.openshift.client.IApplication> type.
     */
    public void setMode(String mode) {
        this.mode = mode;
    }
}
