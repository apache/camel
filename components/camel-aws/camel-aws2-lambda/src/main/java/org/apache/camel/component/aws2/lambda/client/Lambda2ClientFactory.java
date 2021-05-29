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
package org.apache.camel.component.aws2.lambda.client;

import org.apache.camel.component.aws2.lambda.Lambda2Configuration;
import org.apache.camel.component.aws2.lambda.client.impl.Lambda2ClientOptimizedImpl;
import org.apache.camel.component.aws2.lambda.client.impl.Lambda2ClientStandardImpl;

/**
 * Factory class to return the correct type of AWS Lambda client.
 */
public final class Lambda2ClientFactory {

    private Lambda2ClientFactory() {
    }

    /**
     * Return the correct AWS Lambda client (based on remote vs local).
     * 
     * @param  configuration configuration
     * @return               LambdaClient
     */
    public static Lambda2InternalClient getLambdaClient(Lambda2Configuration configuration) {
        return configuration.isUseDefaultCredentialsProvider()
                ? new Lambda2ClientOptimizedImpl(configuration) : new Lambda2ClientStandardImpl(configuration);
    }
}
