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
package org.apache.camel.component.aws2.cw.client;

import org.apache.camel.component.aws2.cw.Cw2Configuration;
import org.apache.camel.component.aws2.cw.client.impl.Cw2ClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.cw.client.impl.Cw2ClientIAMProfileOptimizedImpl;
import org.apache.camel.component.aws2.cw.client.impl.Cw2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.cw.client.impl.Cw2ClientStandardImpl;

/**
 * Factory class to return the correct type of AWS Cloud Watch client.
 */
public final class Cw2ClientFactory {

    private Cw2ClientFactory() {
    }

    /**
     * Return the correct AWS Cloud Watch client (based on remote vs local).
     *
     * @param  configuration configuration
     * @return               CloudWatchClient
     */
    public static Cw2InternalClient getCloudWatchClient(Cw2Configuration configuration) {
        if (Boolean.TRUE.equals(configuration.isUseDefaultCredentialsProvider())) {
            return new Cw2ClientIAMOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseProfileCredentialsProvider())) {
            return new Cw2ClientIAMProfileOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseSessionCredentials())) {
            return new Cw2ClientSessionTokenImpl(configuration);
        } else {
            return new Cw2ClientStandardImpl(configuration);
        }
    }
}
