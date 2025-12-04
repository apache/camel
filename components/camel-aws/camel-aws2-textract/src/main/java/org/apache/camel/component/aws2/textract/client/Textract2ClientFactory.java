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

package org.apache.camel.component.aws2.textract.client;

import org.apache.camel.component.aws2.textract.Textract2Configuration;
import org.apache.camel.component.aws2.textract.client.impl.Textract2ClientIAMOptimized;
import org.apache.camel.component.aws2.textract.client.impl.Textract2ClientIAMProfileOptimized;
import org.apache.camel.component.aws2.textract.client.impl.Textract2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.textract.client.impl.Textract2ClientStandardImpl;

/**
 * Factory class to return the correct type of AWS Textract client.
 */
public final class Textract2ClientFactory {

    private Textract2ClientFactory() {}

    /**
     * Return the correct aws Textract client (based on remote vs local).
     *
     * @param  configuration configuration
     * @return               TextractClient
     */
    public static Textract2InternalClient getTextractClient(Textract2Configuration configuration) {
        if (Boolean.TRUE.equals(configuration.isUseDefaultCredentialsProvider())) {
            return new Textract2ClientIAMOptimized(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseProfileCredentialsProvider())) {
            return new Textract2ClientIAMProfileOptimized(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseSessionCredentials())) {
            return new Textract2ClientSessionTokenImpl(configuration);
        } else {
            return new Textract2ClientStandardImpl(configuration);
        }
    }
}
