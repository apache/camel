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
package org.apache.camel.component.digitalocean;

import com.myjeeva.digitalocean.impl.DigitalOceanClient;
import org.apache.camel.component.digitalocean.constants.DigitalOceanOperations;
import org.apache.camel.component.digitalocean.constants.DigitalOceanResources;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class DigitalOceanConfiguration {

    @UriPath(enums = "create,update,delete,list,ownList,get,listBackups,listActions,listNeighbors,listSnapshots,listKernels,listAllNeighbors,"
        + "enableBackups,disableBackups,reboot,powerCycle,shutdown,powerOn,powerOff,restore,resetPassword,"
        + "resize,rebuild,rename,changeKernel,enableIpv6,enablePrivateNetworking,takeSnapshot,transfer,convert,"
        + "attach,detach,assign,unassign,tag,untag")
    private DigitalOceanOperations operation;

    @UriParam(enums = "account,actions,blockStorages,droplets,mages,snapshots,keys,regions,sizes,floatingIPs,tags")
    @Metadata(required = true)
    private DigitalOceanResources resource;

    @UriParam(label = "advanced")
    private DigitalOceanClient digitalOceanClient;

    @UriParam(label = "security", secret = true)
    private String oAuthToken;

    @UriParam(defaultValue = "1")
    private Integer page = 1;

    @UriParam(defaultValue = "25")
    private Integer perPage = 25;

    @UriParam(label = "proxy")
    private String httpProxyHost;
    @UriParam(label = "proxy", secret = true)
    private String httpProxyUser;
    @UriParam(label = "proxy", secret = true)
    private String httpProxyPassword;
    @UriParam(label = "proxy")
    private Integer httpProxyPort;

    public DigitalOceanResources getResource() {
        return resource;
    }

    /**
     * The DigitalOcean resource type on which perform the operation.
     */
    public void setResource(DigitalOceanResources resource) {
        this.resource = resource;
    }

    public DigitalOceanOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform to the given resource.
     */
    public void setOperation(DigitalOceanOperations operation) {
        this.operation = operation;
    }

    public String getOAuthToken() {
        return oAuthToken;
    }

    /**
     * DigitalOcean OAuth Token
     */
    public void setOAuthToken(String oAuthToken) {
        this.oAuthToken = oAuthToken;
    }

    public Integer getPerPage() {
        return perPage;
    }

    /**
     * Use for pagination. Set the number of item per request. The maximum number of results per page is 200.
     */
    public void setPerPage(Integer perPage) {
        this.perPage = perPage;
    }

    public Integer getPage() {
        return page;
    }

    /**
     * Use for pagination. Force the page number.
     */
    public void setPage(Integer page) {
        this.page = page;
    }

    public String getHttpProxyHost() {
        return httpProxyHost;
    }

    /**
     * Set a proxy host if needed
     */
    public void setHttpProxyHost(String httpProxyHost) {
        this.httpProxyHost = httpProxyHost;
    }

    public String getHttpProxyUser() {
        return httpProxyUser;
    }

    /**
     * Set a proxy host if needed
     */
    public void setHttpProxyUser(String httpProxyUser) {
        this.httpProxyUser = httpProxyUser;
    }

    public String getHttpProxyPassword() {
        return httpProxyPassword;
    }

    /**
     * Set a proxy password if needed
     */
    public void setHttpProxyPassword(String httpProxyPassword) {
        this.httpProxyPassword = httpProxyPassword;
    }

    public Integer getHttpProxyPort() {
        return httpProxyPort;
    }

    /**
     * Set a proxy port if needed
     */
    public void setHttpProxyPort(Integer httpProxyPort) {
        this.httpProxyPort = httpProxyPort;
    }

    public DigitalOceanClient getDigitalOceanClient() {
        return digitalOceanClient;
    }

    /**
     * To use a existing configured DigitalOceanClient as client
     */
    public void setDigitalOceanClient(DigitalOceanClient digitalOceanClient) {
        this.digitalOceanClient = digitalOceanClient;
    }
}
