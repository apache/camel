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
package org.apache.camel.component.elasticsearch5;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

/**
 * Represents the component that manages {@link ElasticsearchEndpoint}.
 */
public class ElasticsearchComponent extends UriEndpointComponent {

    @Metadata(label = "advanced")
    private TransportClient client;

    public ElasticsearchComponent() {
        super(ElasticsearchEndpoint.class);
    }

    public ElasticsearchComponent(CamelContext context) {
        super(context, ElasticsearchEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ElasticsearchConfiguration config = new ElasticsearchConfiguration();
        setProperties(config, parameters);
        config.setClusterName(remaining);
        
        config.setTransportAddressesList(parseTransportAddresses(config.getTransportAddresses(), config));
        
        Endpoint endpoint = new ElasticsearchEndpoint(uri, this, config, client);
        return endpoint;
    }
    
    private List<InetSocketTransportAddress> parseTransportAddresses(String ipsString, ElasticsearchConfiguration config) throws UnknownHostException {
        if (ipsString == null || ipsString.isEmpty()) {
            return null;
        }
        List<String> addressesStr = Arrays.asList(ipsString.split(ElasticsearchConstants.TRANSPORT_ADDRESSES_SEPARATOR_REGEX));
        List<InetSocketTransportAddress> addressesTrAd = new ArrayList<InetSocketTransportAddress>(addressesStr.size());
        for (String address : addressesStr) {
            String[] split = address.split(ElasticsearchConstants.IP_PORT_SEPARATOR_REGEX);
            String hostname;
            if (split.length > 0) {
                hostname = split[0];
            } else {
                throw new IllegalArgumentException();
            }
            Integer port = split.length > 1 ? Integer.parseInt(split[1]) : ElasticsearchConstants.DEFAULT_PORT;
            addressesTrAd.add(new InetSocketTransportAddress(InetAddress.getByName(hostname), port));
        }
        return addressesTrAd;
    }

    public TransportClient getClient() {
        return client;
    }

    /**
     * To use an existing configured Elasticsearch client, instead of creating a client per endpoint.
     * This allow to customize the client with specific settings.
     */
    public void setClient(TransportClient client) {
        this.client = client;
    }
}
