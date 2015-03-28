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
package org.apache.camel.component.geocoder;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * Represents a GeoCoder endpoint.
 */
@UriEndpoint(scheme = "geocoder", title = "Geocoder", syntax = "geocoder:address:latlng", producerOnly = true, label = "api,location")
public class GeoCoderEndpoint extends DefaultEndpoint {

    @UriPath(description = "The geo address which must prefix with address:")
    private String address;
    @UriPath(description = "The geo coordinate which must prefix with latng:")
    private String latlng;
    @UriParam(defaultValue = "en")
    private String language = "en";
    @UriParam
    private String clientId;
    @UriParam
    private String clientKey;
    @UriParam
    private boolean headersOnly;

    public GeoCoderEndpoint() {
    }

    public GeoCoderEndpoint(String uri, GeoCoderComponent component) {
        super(uri, component);
    }

    public Producer createProducer() throws Exception {
        return new GeoCoderProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from this component");
    }

    public boolean isSingleton() {
        return true;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getLatlng() {
        return latlng;
    }

    public void setLatlng(String latlng) {
        this.latlng = latlng;
    }

    public boolean isHeadersOnly() {
        return headersOnly;
    }

    public void setHeadersOnly(boolean headersOnly) {
        this.headersOnly = headersOnly;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientKey() {
        return clientKey;
    }

    public void setClientKey(String clientKey) {
        this.clientKey = clientKey;
    }
}
