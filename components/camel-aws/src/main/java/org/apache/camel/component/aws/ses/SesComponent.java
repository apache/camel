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
package org.apache.camel.component.aws.ses;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

public class SesComponent extends DefaultComponent {

    public SesComponent() {
        this(null);
    }

    public SesComponent(CamelContext context) {
        super(context);
        
        registerExtension(new SesComponentVerifierExtension());
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SesConfiguration configuration = new SesConfiguration();
        setProperties(configuration, parameters);

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("From must be specified.");
        }
        configuration.setFrom(remaining);

        if (configuration.getAmazonSESClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("AmazonSESClient or accessKey and secretKey must be specified");
        }

        return new SesEndpoint(uri, this, configuration);
    }
}
