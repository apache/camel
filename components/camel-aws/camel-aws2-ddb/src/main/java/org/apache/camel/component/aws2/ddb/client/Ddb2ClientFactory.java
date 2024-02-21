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
package org.apache.camel.component.aws2.ddb.client;

import org.apache.camel.component.aws2.ddb.Ddb2Configuration;
import org.apache.camel.component.aws2.ddb.client.impl.Ddb2ClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.ddb.client.impl.Ddb2ClientIAMProfileOptimizedImpl;
import org.apache.camel.component.aws2.ddb.client.impl.Ddb2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.ddb.client.impl.Ddb2ClientStandardImpl;

/**
 * Factory class to return the correct type of AWS DynamoDB client.
 */
public final class Ddb2ClientFactory {

    private Ddb2ClientFactory() {
    }

    /**
     * Return the correct AWS DynamoDB client (based on remote vs local).
     *
     * @param  configuration configuration
     * @return               DynamoDBClient
     */
    public static Ddb2InternalClient getDynamoDBClient(Ddb2Configuration configuration) {
        if (Boolean.TRUE.equals(configuration.isUseDefaultCredentialsProvider())) {
            return new Ddb2ClientIAMOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseProfileCredentialsProvider())) {
            return new Ddb2ClientIAMProfileOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseSessionCredentials())) {
            return new Ddb2ClientSessionTokenImpl(configuration);
        } else {
            return new Ddb2ClientStandardImpl(configuration);
        }
    }
}
