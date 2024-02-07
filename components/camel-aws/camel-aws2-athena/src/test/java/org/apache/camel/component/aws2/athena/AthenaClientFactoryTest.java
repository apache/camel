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
package org.apache.camel.component.aws2.athena;

import org.apache.camel.component.aws2.athena.client.Athena2ClientFactory;
import org.apache.camel.component.aws2.athena.client.Athena2InternalClient;
import org.apache.camel.component.aws2.athena.client.impl.Athena2ClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.athena.client.impl.Athena2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.athena.client.impl.Athena2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AthenaClientFactoryTest {

    @Test
    public void getStandardAthenaClientDefault() {
        Athena2Configuration athena2Configuration = new Athena2Configuration();
        Athena2InternalClient athenaClient = Athena2ClientFactory.getAWSAthenaClient(athena2Configuration);
        assertTrue(athenaClient instanceof Athena2ClientStandardImpl);
    }

    @Test
    public void getStandardAthenaClient() {
        Athena2Configuration athena2Configuration = new Athena2Configuration();
        athena2Configuration.setUseDefaultCredentialsProvider(false);
        Athena2InternalClient athenaClient = Athena2ClientFactory.getAWSAthenaClient(athena2Configuration);
        assertTrue(athenaClient instanceof Athena2ClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedAthenaClient() {
        Athena2Configuration athena2Configuration = new Athena2Configuration();
        athena2Configuration.setUseDefaultCredentialsProvider(true);
        Athena2InternalClient athenaClient = Athena2ClientFactory.getAWSAthenaClient(athena2Configuration);
        assertTrue(athenaClient instanceof Athena2ClientIAMOptimizedImpl);
    }

    @Test
    public void getSessionTokenAthenaClient() {
        Athena2Configuration athena2Configuration = new Athena2Configuration();
        athena2Configuration.setUseSessionCredentials(true);
        Athena2InternalClient athenaClient = Athena2ClientFactory.getAWSAthenaClient(athena2Configuration);
        assertTrue(athenaClient instanceof Athena2ClientSessionTokenImpl);
    }
}
