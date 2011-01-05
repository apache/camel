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
package org.apache.camel.component.aws.sqs;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

/**
 * Defines the <a href="http://camel.apache.org/aws.html">AWS Component</a> 
 * 
 * @version $Revision: $
 */
public class SqsComponent extends DefaultComponent {
    
    public SqsComponent() {
    }

    public SqsComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SqsConfiguration configuration = new SqsConfiguration();
        setProperties(configuration, parameters);

        if (remaining == null) {
            throw new IllegalArgumentException("Queue name not specified.");
        }
        configuration.setQueueName(remaining);

        if (configuration.getAmazonSQSClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("AmazonSQSClient or accessKey and secretKey must be set");
        }

        SqsEndpoint sqsEndpoint = new SqsEndpoint(uri, this, configuration);
        sqsEndpoint.setConsumerProperties(parameters);
        return sqsEndpoint;
    }
}