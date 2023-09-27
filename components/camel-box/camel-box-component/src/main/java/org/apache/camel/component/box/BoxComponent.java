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
package org.apache.camel.component.box;

import com.box.sdk.BoxAPIConnection;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.BoxApiName;
import org.apache.camel.component.box.internal.BoxConnectionHelper;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.component.AbstractApiComponent;

@Component("box")
public class BoxComponent extends AbstractApiComponent<BoxApiName, BoxConfiguration, BoxApiCollection> {

    @Metadata
    BoxConfiguration configuration; // needed for documentation generation

    @Metadata(label = "advanced")
    BoxAPIConnection boxConnection;

    public BoxComponent() {
        super(BoxApiName.class, BoxApiCollection.getCollection());
    }

    public BoxComponent(CamelContext context) {
        super(context, BoxApiName.class, BoxApiCollection.getCollection());
    }

    @Override
    protected BoxApiName getApiName(String apiNameStr) {
        return getCamelContext().getTypeConverter().convertTo(BoxApiName.class, apiNameStr);
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(BoxConfiguration configuration) {
        super.setConfiguration(configuration);
    }

    /**
     * To use the shared configuration
     */
    @Override
    public BoxConfiguration getConfiguration() {
        return super.getConfiguration();
    }

    /**
     * To use a shared connection
     *
     * @return the shared connection
     */
    public BoxAPIConnection getBoxConnection() {
        return boxConnection;
    }

    @Override
    protected Endpoint createEndpoint(
            String uri, String methodName, BoxApiName apiName,
            BoxConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new BoxEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (boxConnection == null) {
            if (getConfiguration() != null) {
                boxConnection = BoxConnectionHelper.createConnection(getConfiguration());
            } else {
                throw new IllegalArgumentException("Unable to connect, Box component configuration is missing");
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (boxConnection != null) {
            boxConnection = null;
        }
    }

    @Override
    public void doShutdown() throws Exception {
        if (boxConnection != null) {
            boxConnection = null;
        }
        super.doShutdown();
    }
}
