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
package org.apache.camel.component.aws2.sts;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * For working with Amazon STS SDK v2.
 */
@Component("aws2-sts")
public class STS2Component extends DefaultComponent {
    @Metadata
    private STS2Configuration configuration = new STS2Configuration();

    public STS2Component() {
        this(null);
    }

    public STS2Component(CamelContext context) {
        super(context);

        registerExtension(new STS2ComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        STS2Configuration configurationClone = this.configuration != null ? this.configuration.copy() : new STS2Configuration();
        STS2Endpoint endpoint = new STS2Endpoint(uri, this, configurationClone);
        setProperties(endpoint, parameters);
        if (Boolean.FALSE.equals(configurationClone.isUseDefaultCredentialsProvider())
                && Boolean.FALSE.equals(configurationClone.isUseProfileCredentialsProvider())
                && configurationClone.getStsClient() == null
                && (configurationClone.getAccessKey() == null || configurationClone.getSecretKey() == null)) {
            throw new IllegalArgumentException(
                    "useDefaultCredentialsProvider is set to false, useProfileCredentialsProvider is set to false, Amazon STS client or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    public STS2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration
     */
    public void setConfiguration(STS2Configuration configuration) {
        this.configuration = configuration;
    }
}
