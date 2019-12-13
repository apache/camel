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
package org.apache.camel.component.consul.endpoint;

import com.orbitz.consul.CatalogClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.model.catalog.CatalogDeregistration;
import com.orbitz.consul.model.catalog.CatalogRegistration;
import org.apache.camel.Message;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.component.consul.ConsulConstants;
import org.apache.camel.component.consul.ConsulEndpoint;
import org.apache.camel.spi.InvokeOnHeader;

public final class ConsulCatalogProducer extends AbstractConsulProducer<CatalogClient> {

    public ConsulCatalogProducer(ConsulEndpoint endpoint, ConsulConfiguration configuration) {
        super(endpoint, configuration, Consul::catalogClient);
    }

    @InvokeOnHeader(ConsulCatalogActions.REGISTER)
    protected void register(Message message) throws Exception {
        getClient().register(message.getMandatoryBody(CatalogRegistration.class));
        setBodyAndResult(message, null);
    }

    @InvokeOnHeader(ConsulCatalogActions.DEREGISTER)
    protected void deregister(Message message) throws Exception {
        getClient().deregister(message.getMandatoryBody(CatalogDeregistration.class));
        setBodyAndResult(message, null);
    }

    @InvokeOnHeader(ConsulCatalogActions.LIST_DATACENTERS)
    protected void listDatacenters(Message message) throws Exception {
        setBodyAndResult(message, getClient().getDatacenters());
    }

    @InvokeOnHeader(ConsulCatalogActions.LIST_NODES)
    protected void listNodes(Message message) throws Exception {
        processConsulResponse(message, getClient().getNodes(buildQueryOptions(message, getConfiguration())));
    }

    @InvokeOnHeader(ConsulCatalogActions.LIST_SERVICES)
    protected void listServices(Message message) throws Exception {
        processConsulResponse(message, getClient().getNodes(buildQueryOptions(message, getConfiguration())));
    }

    @InvokeOnHeader(ConsulCatalogActions.GET_SERVICE)
    protected void getService(Message message) throws Exception {
        processConsulResponse(message,
                              getClient().getService(getMandatoryHeader(message, ConsulConstants.CONSUL_SERVICE, String.class), buildQueryOptions(message, getConfiguration())));
    }

    @InvokeOnHeader(ConsulCatalogActions.GET_NODE)
    protected void getNode(Message message) throws Exception {
        processConsulResponse(message, getClient().getNode(getMandatoryHeader(message, ConsulConstants.CONSUL_NODE, String.class), buildQueryOptions(message, getConfiguration())));
    }
}
