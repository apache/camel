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
package org.apache.camel.component.zendesk;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.zendesk.internal.ZendeskApiCollection;
import org.apache.camel.component.zendesk.internal.ZendeskApiName;
import org.apache.camel.component.zendesk.internal.ZendeskHelper;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.component.AbstractApiComponent;
import org.zendesk.client.v2.Zendesk;

/**
 * The Zendesk Component.
 */
public class ZendeskComponent extends AbstractApiComponent<ZendeskApiName, ZendeskConfiguration, ZendeskApiCollection> {

    @Metadata(label = "advanced")
    Zendesk zendesk;

    public ZendeskComponent() {
        super(ZendeskEndpoint.class, ZendeskApiName.class, ZendeskApiCollection.getCollection());
    }

    public ZendeskComponent(CamelContext context) {
        super(context, ZendeskEndpoint.class, ZendeskApiName.class, ZendeskApiCollection.getCollection());
    }

    @Override
    protected ZendeskApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return ZendeskApiName.fromValue(apiNameStr);
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(ZendeskConfiguration configuration) {
        super.setConfiguration(configuration);
    }

    /**
     * To use the shared configuration
     */
    @Override
    public ZendeskConfiguration getConfiguration() {
        return super.getConfiguration();
    }

    /**
     * To use a shared {@link Zendesk} instance.
     * 
     * @return the shared Zendesk instance
     */
    public Zendesk getZendesk() {
        return zendesk;
    }

    public void setZendesk(Zendesk zendesk) {
        this.zendesk = zendesk;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String methodName, ZendeskApiName apiName,
            ZendeskConfiguration endpointConfiguration) {
        endpointConfiguration.setMethodName(methodName);
        return new ZendeskEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (zendesk == null && configuration != null) {
            zendesk = ZendeskHelper.create(configuration);
        }
    }

    @Override
    protected void doStop() throws Exception {
        IOHelper.close(zendesk);
        super.doStop();
    }

    @Override
    public void doShutdown() throws Exception {
        IOHelper.close(zendesk);
        super.doShutdown();
    }

}
