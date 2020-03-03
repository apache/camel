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
package org.apache.camel.component.aws.cw;

import java.util.Map;
import java.util.Set;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("aws-cw")
public class CwComponent extends DefaultComponent {

    @Metadata
    private CwConfiguration configuration = new CwConfiguration();
    
    public CwComponent() {
        this(null);
    }

    public CwComponent(CamelContext context) {
        super(context);
        registerExtension(new CwComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Metric namespace must be specified.");
        }

        CwConfiguration configuration = this.configuration != null ? this.configuration.copy() : new CwConfiguration();
        configuration.setNamespace(remaining);

        CwEndpoint endpoint = new CwEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        checkAndSetRegistryClient(configuration);
        if (configuration.getAmazonCwClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("AmazonCwClient or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    public CwConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(CwConfiguration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(CwConfiguration configuration) {
        Set<AmazonCloudWatch> clients = getCamelContext().getRegistry().findByType(AmazonCloudWatch.class);
        if (clients.size() == 1) {
            configuration.setAmazonCwClient(clients.stream().findFirst().get());
        }
    }
}
