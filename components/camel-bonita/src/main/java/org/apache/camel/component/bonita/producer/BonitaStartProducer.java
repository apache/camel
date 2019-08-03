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
package org.apache.camel.component.bonita.producer;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.bonita.BonitaConfiguration;
import org.apache.camel.component.bonita.BonitaEndpoint;
import org.apache.camel.component.bonita.api.BonitaAPI;
import org.apache.camel.component.bonita.api.BonitaAPIBuilder;
import org.apache.camel.component.bonita.api.model.ProcessDefinitionResponse;
import org.apache.camel.component.bonita.api.util.BonitaAPIConfig;

public class BonitaStartProducer extends BonitaProducer {

    public BonitaStartProducer(BonitaEndpoint endpoint, BonitaConfiguration configuration) {
        super(endpoint, configuration);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // Setup access type (HTTP on local host)
        String hostname = this.configuration.getHostname();
        String port = this.configuration.getPort();
        String processName = this.configuration.getProcessName();
        String username = this.configuration.getUsername();
        String password = this.configuration.getPassword();
        BonitaAPIConfig bonitaAPIConfig = new BonitaAPIConfig(hostname, port, username, password);
        BonitaAPI bonitaApi = BonitaAPIBuilder.build(bonitaAPIConfig);
        ProcessDefinitionResponse processDefinition = bonitaApi.getProcessDefinition(processName);
        bonitaApi.startCase(processDefinition, exchange.getIn().getBody(Map.class));
    }

}
