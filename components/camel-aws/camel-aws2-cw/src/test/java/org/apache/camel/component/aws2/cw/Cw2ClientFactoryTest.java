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
package org.apache.camel.component.aws2.cw;

import org.apache.camel.component.aws2.cw.client.Cw2ClientFactory;
import org.apache.camel.component.aws2.cw.client.Cw2InternalClient;
import org.apache.camel.component.aws2.cw.client.impl.Cw2ClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.cw.client.impl.Cw2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.cw.client.impl.Cw2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class Cw2ClientFactoryTest {

    @Test
    public void getStandardCWClientDefault() {
        Cw2Configuration cw2Configuration = new Cw2Configuration();
        Cw2InternalClient cwClient = Cw2ClientFactory.getCloudWatchClient(cw2Configuration);
        assertTrue(cwClient instanceof Cw2ClientStandardImpl);
    }

    @Test
    public void getStandardCWClient() {
        Cw2Configuration cw2Configuration = new Cw2Configuration();
        cw2Configuration.setUseDefaultCredentialsProvider(false);
        Cw2InternalClient cwClient = Cw2ClientFactory.getCloudWatchClient(cw2Configuration);
        assertTrue(cwClient instanceof Cw2ClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedCWClient() {
        Cw2Configuration cw2Configuration = new Cw2Configuration();
        cw2Configuration.setUseDefaultCredentialsProvider(true);
        Cw2InternalClient cwClient = Cw2ClientFactory.getCloudWatchClient(cw2Configuration);
        assertTrue(cwClient instanceof Cw2ClientIAMOptimizedImpl);
    }

    @Test
    public void getSessionTokenCwClient() {
        Cw2Configuration cw2Configuration = new Cw2Configuration();
        cw2Configuration.setUseSessionCredentials(true);
        Cw2InternalClient cwClient = Cw2ClientFactory.getCloudWatchClient(cw2Configuration);
        assertTrue(cwClient instanceof Cw2ClientSessionTokenImpl);
    }
}
