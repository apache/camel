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
package org.apache.camel.component.jbpm;

import java.net.MalformedURLException;
import java.net.URISyntaxException;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.remote.client.api.RemoteRestRuntimeEngineBuilder;
import org.kie.services.client.api.RemoteRuntimeEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The jbpm component provides integration with jBPM (Business Process Management).
 */
@UriEndpoint(firstVersion = "2.6.0", scheme = "jbpm", title = "JBPM", syntax = "jbpm:connectionURL", producerOnly = true, label = "process")
public class JBPMEndpoint extends DefaultEndpoint {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(JBPMEndpoint.class);

    @UriParam
    private JBPMConfiguration configuration;

    public JBPMEndpoint(String uri, JBPMComponent component, JBPMConfiguration configuration) throws URISyntaxException, MalformedURLException {
        super(uri, component);
        this.configuration = configuration;
    }

    public Producer createProducer() throws Exception {
        RemoteRestRuntimeEngineBuilder engineBuilder = RemoteRuntimeEngineFactory.newRestBuilder();
        if (configuration.getUserName() != null) {
            engineBuilder.addUserName(configuration.getUserName());
        }
        if (configuration.getPassword() != null) {
            engineBuilder.addPassword(configuration.getPassword());
        }
        if (configuration.getDeploymentId() != null) {
            engineBuilder.addDeploymentId(configuration.getDeploymentId());
        }
        if (configuration.getConnectionURL() != null) {
            engineBuilder.addUrl(configuration.getConnectionURL());
        }
        if (configuration.getProcessInstanceId() != null) {
            engineBuilder.addProcessInstanceId(configuration.getProcessInstanceId());
        }
        if (configuration.getTimeout() != null) {
            engineBuilder.addTimeout(configuration.getTimeout());
        }
        if (configuration.getExtraJaxbClasses() != null) {
            engineBuilder.addExtraJaxbClasses(configuration.getExtraJaxbClasses());
        }
        RuntimeEngine runtimeEngine = engineBuilder.build();

        return new JBPMProducer(this, runtimeEngine);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported for " + getClass().getSimpleName() + " endpoint");
    }

    public boolean isSingleton() {
        return true;
    }

    public void setConfiguration(JBPMConfiguration configuration) {
        this.configuration = configuration;
    }

    public JBPMConfiguration getConfiguration() {
        return configuration;
    }
}
