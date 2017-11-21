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
package org.apache.camel.component.aws.ec2;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

/**
 * For working with Amazon's Elastic Compute Cloud (EC2).
 */
public class EC2Component extends DefaultComponent {

    public EC2Component() {
        this(null);
    }
    
    public EC2Component(CamelContext context) {
        super(context);
        
        registerExtension(new EC2ComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        EC2Configuration configuration = new EC2Configuration();
        setProperties(configuration, parameters);

        if (configuration.getAmazonEc2Client() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("amazonEC2Client or accessKey and secretKey must be specified");
        }
        
        EC2Endpoint endpoint = new EC2Endpoint(uri, this, configuration);
        return endpoint;
    }

}
