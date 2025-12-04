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

package org.apache.camel.component.aws2.textract;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.component.aws2.textract.client.Textract2ClientFactory;
import org.apache.camel.component.aws2.textract.client.Textract2InternalClient;
import org.apache.camel.component.aws2.textract.client.impl.Textract2ClientIAMOptimized;
import org.apache.camel.component.aws2.textract.client.impl.Textract2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.textract.client.impl.Textract2ClientStandardImpl;
import org.junit.jupiter.api.Test;

public class Textract2ClientFactoryTest {

    @Test
    public void getStandardTextractClientDefault() {
        Textract2Configuration textract2Configuration = new Textract2Configuration();
        Textract2InternalClient textractClient = Textract2ClientFactory.getTextractClient(textract2Configuration);
        assertTrue(textractClient instanceof Textract2ClientStandardImpl);
    }

    @Test
    public void getStandardTextractClient() {
        Textract2Configuration textract2Configuration = new Textract2Configuration();
        textract2Configuration.setUseDefaultCredentialsProvider(false);
        Textract2InternalClient textractClient = Textract2ClientFactory.getTextractClient(textract2Configuration);
        assertTrue(textractClient instanceof Textract2ClientStandardImpl);
    }

    @Test
    public void getTextractOptimizedIAMClient() {
        Textract2Configuration textract2Configuration = new Textract2Configuration();
        textract2Configuration.setUseDefaultCredentialsProvider(true);
        Textract2InternalClient textractClient = Textract2ClientFactory.getTextractClient(textract2Configuration);
        assertTrue(textractClient instanceof Textract2ClientIAMOptimized);
    }

    @Test
    public void getTextractSessionTokenClient() {
        Textract2Configuration textract2Configuration = new Textract2Configuration();
        textract2Configuration.setUseSessionCredentials(true);
        Textract2InternalClient textractClient = Textract2ClientFactory.getTextractClient(textract2Configuration);
        assertTrue(textractClient instanceof Textract2ClientSessionTokenImpl);
    }
}
