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
package org.apache.camel.component.aws2.redshift.data;

import org.apache.camel.component.aws2.redshift.data.client.RedshiftData2ClientFactory;
import org.apache.camel.component.aws2.redshift.data.client.RedshiftData2InternalClient;
import org.apache.camel.component.aws2.redshift.data.client.impl.RedshiftData2ClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.redshift.data.client.impl.RedshiftData2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.redshift.data.client.impl.RedshiftData2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class RedshiftData2ClientFactoryTest {

    @Test
    public void getStandardRedshiftDataClientDefault() {
        RedshiftData2Configuration redshiftData2Configuration = new RedshiftData2Configuration();
        RedshiftData2InternalClient redshiftDataClient
                = RedshiftData2ClientFactory.getRedshiftDataClient(redshiftData2Configuration);
        assertTrue(redshiftDataClient instanceof RedshiftData2ClientStandardImpl);
    }

    @Test
    public void getStandardRedshiftDataClient() {
        RedshiftData2Configuration redshiftData2Configuration = new RedshiftData2Configuration();
        redshiftData2Configuration.setUseDefaultCredentialsProvider(false);
        RedshiftData2InternalClient redshiftDataClient
                = RedshiftData2ClientFactory.getRedshiftDataClient(redshiftData2Configuration);
        assertTrue(redshiftDataClient instanceof RedshiftData2ClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedRedshiftDataClient() {
        RedshiftData2Configuration redshiftData2Configuration = new RedshiftData2Configuration();
        redshiftData2Configuration.setUseDefaultCredentialsProvider(true);
        RedshiftData2InternalClient redshiftDataClient
                = RedshiftData2ClientFactory.getRedshiftDataClient(redshiftData2Configuration);
        assertTrue(redshiftDataClient instanceof RedshiftData2ClientIAMOptimizedImpl);
    }

    @Test
    public void getSessionTokenRedshiftDataClient() {
        RedshiftData2Configuration redshiftData2Configuration = new RedshiftData2Configuration();
        redshiftData2Configuration.setUseSessionCredentials(true);
        RedshiftData2InternalClient redshiftDataClient
                = RedshiftData2ClientFactory.getRedshiftDataClient(redshiftData2Configuration);
        assertTrue(redshiftDataClient instanceof RedshiftData2ClientSessionTokenImpl);
    }
}
