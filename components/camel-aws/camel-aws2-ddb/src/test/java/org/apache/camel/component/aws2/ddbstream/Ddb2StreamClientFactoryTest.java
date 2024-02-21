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
package org.apache.camel.component.aws2.ddbstream;

import org.apache.camel.component.aws2.ddbstream.client.Ddb2StreamClientFactory;
import org.apache.camel.component.aws2.ddbstream.client.Ddb2StreamInternalClient;
import org.apache.camel.component.aws2.ddbstream.client.impl.Ddb2StreamClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.ddbstream.client.impl.Ddb2StreamClientSessionTokenImpl;
import org.apache.camel.component.aws2.ddbstream.client.impl.Ddb2StreamClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class Ddb2StreamClientFactoryTest {

    @Test
    public void getStandardDdb2StreamClientDefault() {
        Ddb2StreamConfiguration ddb2StreamConfiguration = new Ddb2StreamConfiguration();
        Ddb2StreamInternalClient ddb2StreamClient = Ddb2StreamClientFactory.getDynamoDBStreamClient(ddb2StreamConfiguration);
        assertTrue(ddb2StreamClient instanceof Ddb2StreamClientStandardImpl);
    }

    @Test
    public void getStandardDdb2StreamClient() {
        Ddb2StreamConfiguration ddb2StreamConfiguration = new Ddb2StreamConfiguration();
        ddb2StreamConfiguration.setUseDefaultCredentialsProvider(false);
        Ddb2StreamInternalClient ddb2StreamClient = Ddb2StreamClientFactory.getDynamoDBStreamClient(ddb2StreamConfiguration);
        assertTrue(ddb2StreamClient instanceof Ddb2StreamClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedDdb2StreamClient() {
        Ddb2StreamConfiguration ddb2StreamConfiguration = new Ddb2StreamConfiguration();
        ddb2StreamConfiguration.setUseDefaultCredentialsProvider(true);
        Ddb2StreamInternalClient ddb2StreamClient = Ddb2StreamClientFactory.getDynamoDBStreamClient(ddb2StreamConfiguration);
        assertTrue(ddb2StreamClient instanceof Ddb2StreamClientIAMOptimizedImpl);
    }

    @Test
    public void getSessionTokenDdb2StreamClient() {
        Ddb2StreamConfiguration ddb2StreamConfiguration = new Ddb2StreamConfiguration();
        ddb2StreamConfiguration.setUseSessionCredentials(true);
        Ddb2StreamInternalClient ddb2StreamClient = Ddb2StreamClientFactory.getDynamoDBStreamClient(ddb2StreamConfiguration);
        assertTrue(ddb2StreamClient instanceof Ddb2StreamClientSessionTokenImpl);
    }
}
