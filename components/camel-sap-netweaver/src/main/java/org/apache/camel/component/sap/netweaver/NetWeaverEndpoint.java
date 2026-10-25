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
package org.apache.camel.component.sap.netweaver;

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Send requests to SAP NetWeaver Gateway using HTTP.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "sap-netweaver", title = "SAP NetWeaver", syntax = "sap-netweaver:url",
             producerOnly = true, category = { Category.SAAS },
             headersClass = NetWeaverConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The body is not sent: the producer makes a GET of the OData command in the"
                                                + " CamelNetWeaverCommand header"),
           producerOut = @BodyType(value = { Carrier.MAP, Carrier.LIST, Carrier.NULL },
                                   byOption = {
                                           @BodyType.When(option = "flatternMap", value = "false",
                                                          carriers = { Carrier.MAP, Carrier.NULL }),
                                           @BodyType.When(option = "jsonAsMap", value = "false",
                                                          carriers = { Carrier.TEXT, Carrier.NULL }),
                                           @BodyType.When(option = "json", value = "false",
                                                          carriers = { Carrier.TEXT, Carrier.NULL }) },
                                   description = "The JSON response read into a Map; when it has one entry, such as the d of"
                                                 + " OData, the value of that entry instead, a Map or a List. Null for a response"
                                                 + " without a body; a JSON array or a response that is not JSON fails. With"
                                                 + " jsonAsMap=false or json=false the response as text"))
public class NetWeaverEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriPath
    @Metadata(required = true)
    private String url;
    @UriParam(defaultValue = "true")
    private boolean json = true;
    @UriParam(defaultValue = "true")
    private boolean jsonAsMap = true;
    @UriParam(defaultValue = "true")
    private boolean flatternMap = true;
    @UriParam
    @Metadata(required = true, security = "secret")
    private String username;
    @UriParam
    @Metadata(required = true, security = "secret")
    private String password;

    public NetWeaverEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public String getServiceUrl() {
        return url;
    }

    @Override
    public String getServiceProtocol() {
        return "http";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (username != null) {
            return Map.of("username", username);
        }
        return null;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new NetWeaverProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not supported");
    }

    public String getUrl() {
        return url;
    }

    /**
     * Url to the SAP net-weaver gateway server.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Username for account.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password for account.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isJson() {
        return json;
    }

    /**
     * Whether to return data in JSON format. If this option is false, then XML is returned in Atom format.
     */
    public void setJson(boolean json) {
        this.json = json;
    }

    public boolean isJsonAsMap() {
        return jsonAsMap;
    }

    /**
     * To transform the JSON from a String to a Map in the message body.
     */
    public void setJsonAsMap(boolean jsonAsMap) {
        this.jsonAsMap = jsonAsMap;
    }

    public boolean isFlatternMap() {
        return flatternMap;
    }

    /**
     * If the JSON Map contains only a single entry, then flattern by storing that single entry value as the message
     * body.
     */
    public void setFlatternMap(boolean flatternMap) {
        this.flatternMap = flatternMap;
    }
}
