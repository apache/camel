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
package org.apache.camel.component.iota;

import java.net.URL;

import jota.IotaAPI;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Component for integrate IOTA DLT
 */
@UriEndpoint(firstVersion = "2.23.0", scheme = "iota", title = "IOTA", syntax = "iota:name", label = "dlt")
public class IOTAEndpoint extends DefaultEndpoint {

    private IotaAPI apiClient;

    @UriPath
    @Metadata(required = true)
    private String name;

    @UriParam
    private String url;

    @UriParam
    private String operation;
    @UriParam
    private String tag;
    @UriParam(defaultValue = "1")
    private Integer securityLevel = 1;
    @UriParam(defaultValue = "14")
    private Integer minWeightMagnitude = IOTAConstants.MIN_WEIGHT_MAGNITUDE;
    @UriParam(defaultValue = "9")
    private Integer depth = IOTAConstants.DEPTH;

    public IOTAEndpoint() {
    }

    public IOTAEndpoint(String uri, IOTAComponent component) {
        super(uri, component);
    }

    public Producer createProducer() throws Exception {
        return new IOTAProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("IOTAConsumer is not supported!");
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        final URL u = new URL(url);
        apiClient = new IotaAPI.Builder().protocol(u.getProtocol()).host(u.getHost()).port(String.valueOf(u.getPort())).build();

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        apiClient = null;
    }

    public String getName() {
        return name;
    }

    /**
     * Component name
     * 
     * @param url
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    /**
     * Node url
     * 
     * @param url
     */
    public void setUrl(String url) {
        this.url = url;
    }

    public String getTag() {
        return tag;
    }

    /**
     * TAG
     * 
     * @param tag
     */
    public void setTag(String tag) {
        this.tag = tag;
    }

    public Integer getSecurityLevel() {
        return securityLevel;
    }

    /**
     * Address security level
     * 
     * @param security level
     */
    public void setSecurityLevel(Integer securityLevel) {
        this.securityLevel = securityLevel;
    }

    public Integer getMinWeightMagnitude() {
        return minWeightMagnitude;
    }

    /**
     * The minWeightMagnitude is the minimum number of zeroes that a
     * proof-of-work output/transaction hash must end with to be considered
     * valid by full nodes
     * 
     * @param minWeightMagnitude
     */

    public void setMinWeightMagnitude(Integer minWeightMagnitude) {
        this.minWeightMagnitude = minWeightMagnitude;
    }

    public Integer getDepth() {
        return depth;
    }

    /**
     * The depth determines how deep the tangle is analysed for getting Tips
     * 
     * @param depth
     */
    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public IotaAPI getApiClient() {
        return apiClient;
    }

    public String getOperation() {
        return operation;
    }

    /**
     * Supported operations are 'sendTransfer', 'getNewAddress'
     * 
     * @param operation
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }
}
