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
package org.apache.camel.component.google.functions;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("google-functions")
public class GoogleCloudFunctionsComponent extends DefaultComponent {

    @Metadata
    private GoogleCloudFunctionsConfiguration configuration = new GoogleCloudFunctionsConfiguration();

    public GoogleCloudFunctionsComponent() {
    }

    public GoogleCloudFunctionsComponent(CamelContext context) {
        super(context);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Function name must be specified.");
        }
        final GoogleCloudFunctionsConfiguration configuration
                = this.configuration != null ? this.configuration.copy() : new GoogleCloudFunctionsConfiguration();
        configuration.setFunctionName(remaining);

        Endpoint endpoint = new GoogleCloudFunctionsEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public GoogleCloudFunctionsConfiguration getConfiguration() {
        return configuration;
    }
}
