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
package org.apache.camel.component.aws2.ses;

import org.apache.camel.component.aws2.ses.client.Ses2ClientFactory;
import org.apache.camel.component.aws2.ses.client.Ses2InternalClient;
import org.apache.camel.component.aws2.ses.client.impl.Ses2ClientOptimizedImpl;
import org.apache.camel.component.aws2.ses.client.impl.Ses2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.ses.client.impl.Ses2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class Ses2ClientFactoryTest {

    @Test
    public void getStandardSESClientDefault() {
        Ses2Configuration ses2Configuration = new Ses2Configuration();
        Ses2InternalClient sesClient = Ses2ClientFactory.getSesClient(ses2Configuration);
        assertTrue(sesClient instanceof Ses2ClientStandardImpl);
    }

    @Test
    public void getStandardSESClient() {
        Ses2Configuration ses2Configuration = new Ses2Configuration();
        ses2Configuration.setUseDefaultCredentialsProvider(false);
        Ses2InternalClient sesClient = Ses2ClientFactory.getSesClient(ses2Configuration);
        assertTrue(sesClient instanceof Ses2ClientStandardImpl);
    }

    @Test
    public void getSESOptimizedIAMClient() {
        Ses2Configuration ses2Configuration = new Ses2Configuration();
        ses2Configuration.setUseDefaultCredentialsProvider(true);
        Ses2InternalClient sesClient = Ses2ClientFactory.getSesClient(ses2Configuration);
        assertTrue(sesClient instanceof Ses2ClientOptimizedImpl);
    }

    @Test
    public void getSESSessionTokenImplClient() {
        Ses2Configuration ses2Configuration = new Ses2Configuration();
        ses2Configuration.setUseSessionCredentials(true);
        Ses2InternalClient sesClient = Ses2ClientFactory.getSesClient(ses2Configuration);
        assertTrue(sesClient instanceof Ses2ClientSessionTokenImpl);
    }
}
