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
package org.apache.camel.component.aws2.ddb;

import org.apache.camel.component.aws2.ddb.client.Ddb2ClientFactory;
import org.apache.camel.component.aws2.ddb.client.Ddb2InternalClient;
import org.apache.camel.component.aws2.ddb.client.impl.Ddb2ClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.ddb.client.impl.Ddb2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.ddb.client.impl.Ddb2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class Ddb2ClientFactoryTest {

    @Test
    public void getStandardDdb2ClientDefault() {
        Ddb2Configuration ddb2Configuration = new Ddb2Configuration();
        Ddb2InternalClient ddb2Client = Ddb2ClientFactory.getDynamoDBClient(ddb2Configuration);
        assertTrue(ddb2Client instanceof Ddb2ClientStandardImpl);
    }

    @Test
    public void getStandardDdb2Client() {
        Ddb2Configuration ddb2Configuration = new Ddb2Configuration();
        ddb2Configuration.setUseDefaultCredentialsProvider(false);
        Ddb2InternalClient ddb2Client = Ddb2ClientFactory.getDynamoDBClient(ddb2Configuration);
        assertTrue(ddb2Client instanceof Ddb2ClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedDdb2Client() {
        Ddb2Configuration ddb2Configuration = new Ddb2Configuration();
        ddb2Configuration.setUseDefaultCredentialsProvider(true);
        Ddb2InternalClient ddb2Client = Ddb2ClientFactory.getDynamoDBClient(ddb2Configuration);
        assertTrue(ddb2Client instanceof Ddb2ClientIAMOptimizedImpl);
    }

    @Test
    public void getSessionTokenDdb2Client() {
        Ddb2Configuration ddb2Configuration = new Ddb2Configuration();
        ddb2Configuration.setUseSessionCredentials(true);
        Ddb2InternalClient ddb2Client = Ddb2ClientFactory.getDynamoDBClient(ddb2Configuration);
        assertTrue(ddb2Client instanceof Ddb2ClientSessionTokenImpl);
    }
}
