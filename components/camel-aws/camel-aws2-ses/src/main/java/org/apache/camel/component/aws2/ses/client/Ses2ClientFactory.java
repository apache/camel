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
package org.apache.camel.component.aws2.ses.client;

import org.apache.camel.component.aws2.ses.Ses2Configuration;
import org.apache.camel.component.aws2.ses.client.impl.Ses2ClientOptimizedImpl;
import org.apache.camel.component.aws2.ses.client.impl.Ses2ClientProfileOptimizedImpl;
import org.apache.camel.component.aws2.ses.client.impl.Ses2ClientStandardImpl;

/**
 * Factory class to return the correct type of AWS SES client.
 */
public final class Ses2ClientFactory {

    private Ses2ClientFactory() {
    }

    /**
     * Return the correct AWS SES client (based on remote vs local).
     *
     * @param  configuration configuration
     * @return               SesClient
     */
    public static Ses2InternalClient getSesClient(Ses2Configuration configuration) {
        if (Boolean.TRUE.equals(configuration.isUseDefaultCredentialsProvider())) {
            return new Ses2ClientOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseProfileCredentialsProvider())) {
            return new Ses2ClientProfileOptimizedImpl(configuration);
        } else {
            return new Ses2ClientStandardImpl(configuration);
        }
    }
}
