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
package org.apache.camel.component.aws2.msk.client;

import org.apache.camel.component.aws2.msk.MSK2Configuration;
import org.apache.camel.component.aws2.msk.client.impl.MSK2ClientOptimizedImpl;
import org.apache.camel.component.aws2.msk.client.impl.MSK2ClientProfileOptimizedImpl;
import org.apache.camel.component.aws2.msk.client.impl.MSK2ClientStandardImpl;

/**
 * Factory class to return the correct type of AWS Kafka client.
 */
public final class MSK2ClientFactory {

    private MSK2ClientFactory() {
    }

    /**
     * Return the correct AWS Kafka client (based on remote vs local).
     *
     * @param  configuration configuration
     * @return               MqClient
     */
    public static MSK2InternalClient getKafkaClient(MSK2Configuration configuration) {
        if (Boolean.TRUE.equals(configuration.isUseDefaultCredentialsProvider())) {
            return new MSK2ClientOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseProfileCredentialsProvider())) {
            return new MSK2ClientProfileOptimizedImpl(configuration);
        } else {
            return new MSK2ClientStandardImpl(configuration);
        }
    }
}
