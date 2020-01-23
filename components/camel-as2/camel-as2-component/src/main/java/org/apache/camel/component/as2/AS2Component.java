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
package org.apache.camel.component.as2;

import java.security.Security;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.as2.internal.AS2ApiCollection;
import org.apache.camel.component.as2.internal.AS2ApiName;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.component.AbstractApiComponent;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link AS2Endpoint}.
 */
@Component("as2")
public class AS2Component extends AbstractApiComponent<AS2ApiName, AS2Configuration, AS2ApiCollection> {

    private static final Logger LOG = LoggerFactory.getLogger(AS2Component.class);

    public AS2Component() {
        super(AS2Endpoint.class, AS2ApiName.class, AS2ApiCollection.getCollection());
    }

    public AS2Component(CamelContext context) {
        super(context, AS2Endpoint.class, AS2ApiName.class, AS2ApiCollection.getCollection());
    }

    @Override
    protected AS2ApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return AS2ApiName.fromValue(apiNameStr);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String methodName, AS2ApiName apiName,
                                      AS2Configuration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new AS2Endpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(AS2Configuration configuration) {
        super.setConfiguration(configuration);
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (Security.getProvider("BC") == null) {
            LOG.debug("Adding BouncyCastleProvider as security provider");
            Security.addProvider(new BouncyCastleProvider());
        }
    }

}
