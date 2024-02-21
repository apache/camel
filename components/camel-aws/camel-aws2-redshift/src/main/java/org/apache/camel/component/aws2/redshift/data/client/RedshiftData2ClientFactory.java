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
package org.apache.camel.component.aws2.redshift.data.client;

import org.apache.camel.component.aws2.redshift.data.RedshiftData2Configuration;
import org.apache.camel.component.aws2.redshift.data.client.impl.RedshiftData2ClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.redshift.data.client.impl.RedshiftData2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.redshift.data.client.impl.RedshiftData2ClientStandardImpl;

/**
 * Factory class to return the correct type of AWS RedshiftData client.
 */
public final class RedshiftData2ClientFactory {

    private RedshiftData2ClientFactory() {
    }

    /**
     * Return the correct AWS RedshiftData client (based on remote vs local).
     *
     * @param  configuration configuration
     * @return               RedshiftDataClient
     */
    public static RedshiftData2InternalClient getRedshiftDataClient(RedshiftData2Configuration configuration) {
        if (Boolean.TRUE.equals(configuration.isUseDefaultCredentialsProvider())) {
            return new RedshiftData2ClientIAMOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseSessionCredentials())) {
            return new RedshiftData2ClientSessionTokenImpl(configuration);
        } else {
            return new RedshiftData2ClientStandardImpl(configuration);
        }
    }
}
