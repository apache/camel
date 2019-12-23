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
package org.apache.camel.component.jbpm;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The jbpm component provides integration with jBPM (Business Process
 * Management).
 */
@UriEndpoint(firstVersion = "2.6.0", scheme = "jbpm", title = "JBPM", syntax = "jbpm:connectionURL", label = "process")
public class JBPMEndpoint extends DefaultEndpoint {
    private static final transient Logger LOGGER = LoggerFactory.getLogger(JBPMEndpoint.class);

    @UriParam
    private JBPMConfiguration configuration;

    public JBPMEndpoint(String uri, JBPMComponent component, JBPMConfiguration configuration) throws URISyntaxException, MalformedURLException {
        super(uri, component);
        this.configuration = configuration;
    }

    public Producer createProducer() throws Exception {
        KieServicesConfiguration kieConfiguration = KieServicesFactory.newRestConfiguration(configuration.getConnectionURL().toExternalForm(), configuration.getUserName(),
                                                                                            configuration.getPassword());

        if (configuration.getTimeout() != null) {
            kieConfiguration.setTimeout(configuration.getTimeout());
        }
        if (configuration.getExtraJaxbClasses() != null) {
            List<Class<?>> classes = Arrays.asList(configuration.getExtraJaxbClasses());
            kieConfiguration.addExtraClasses(new LinkedHashSet<>(classes));
        }

        KieServicesClient kieServerClient = KieServicesFactory.newKieServicesClient(kieConfiguration);
        LOGGER.debug("JBPM Producer created with KieServerClient configured for {}", configuration.getConnectionURL());
        return new JBPMProducer(this, kieServerClient);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        LOGGER.debug("JBPM Consumer created and configured for deployment {}", configuration.getDeploymentId());
        JBPMConsumer consumer = new JBPMConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public void setConfiguration(JBPMConfiguration configuration) {
        this.configuration = configuration;
    }

    public JBPMConfiguration getConfiguration() {
        return configuration;
    }
}
