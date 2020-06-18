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
package org.apache.camel.component.minio;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Represents the component that manages {@link MinioEndpoint}.
 */
@Component("minio")
public class MinioComponent extends DefaultComponent {

    @Metadata
    private MinioConfiguration configuration = new MinioConfiguration();

    public MinioComponent() {
        super();
    }

    public MinioComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected MinioEndpoint createEndpoint(String uri, String remaining, java.util.Map<String, Object> parameters)
            throws Exception {
        setProperties(configuration, parameters);
        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Bucket name must be specified.");
        }
        if (remaining.startsWith("arn:")) {
            remaining = remaining.substring(remaining.lastIndexOf(":") + 1, remaining.length());
        }
        configuration.setBucketName(remaining);

        final MinioEndpoint endpoint = new MinioEndpoint(uri, remaining, this, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public MinioConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(MinioConfiguration configuration) {
        this.configuration = configuration;
    }

}
