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
package org.apache.camel.component.corda;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("corda")
public class CordaComponent extends DefaultComponent {

    @Metadata
    private CordaConfiguration configuration;

    public CordaConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use a shared configuration.
     */
    public void setConfiguration(CordaConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected Endpoint createEndpoint(String uri, final String remaining, final Map<String, Object> parameters) throws Exception {
        CordaConfiguration conf =  configuration != null ? configuration.copy() : new CordaConfiguration();
        conf.setNode(remaining);

        CordaEndpoint endpoint = new CordaEndpoint(uri, this, conf);
        setProperties(endpoint, parameters);
        conf.configure();
        return endpoint;
    }

}
