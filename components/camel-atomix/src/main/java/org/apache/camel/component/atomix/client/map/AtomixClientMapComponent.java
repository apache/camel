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
package org.apache.camel.component.atomix.client.map;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.atomix.client.AbstractAtomixClientComponent;

public class AtomixClientMapComponent extends AbstractAtomixClientComponent<AtomixClientMapConfiguration> {
    private AtomixClientMapConfiguration configuration = new AtomixClientMapConfiguration();

    public AtomixClientMapComponent() {
        super();
    }

    public AtomixClientMapComponent(CamelContext camelContext) {
        super(camelContext);
    }

    // **********************************************
    // Endpoints
    // **********************************************

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        AtomixClientMapConfiguration configuration = this.configuration.copy();
        setProperties(configuration, parameters);

        return new AtomixClientMapEndpoint(uri, this, configuration, remaining);
    }

    // **********************************************
    // Properties
    // **********************************************

    public AtomixClientMapConfiguration getConfiguration() {
        return this.configuration;
    }

    /**
     * The shared component configuration
     */
    public void setConfiguration(AtomixClientMapConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected AtomixClientMapConfiguration getComponentConfiguration() {
        return getConfiguration();
    }
}
