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
package org.apache.camel.component.aws2.translate;

import org.apache.camel.component.aws2.translate.client.Translate2ClientFactory;
import org.apache.camel.component.aws2.translate.client.Translate2InternalClient;
import org.apache.camel.component.aws2.translate.client.impl.Translate2ClientIAMOptimized;
import org.apache.camel.component.aws2.translate.client.impl.Translate2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.translate.client.impl.Translate2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class Translate2ClientFactoryTest {

    @Test
    public void getStandardTranslateClientDefault() {
        Translate2Configuration translate2Configuration = new Translate2Configuration();
        Translate2InternalClient translateClient = Translate2ClientFactory.getTranslateClient(translate2Configuration);
        assertTrue(translateClient instanceof Translate2ClientStandardImpl);
    }

    @Test
    public void getStandardTranslateClient() {
        Translate2Configuration translate2Configuration = new Translate2Configuration();
        translate2Configuration.setUseDefaultCredentialsProvider(false);
        Translate2InternalClient translateClient = Translate2ClientFactory.getTranslateClient(translate2Configuration);
        assertTrue(translateClient instanceof Translate2ClientStandardImpl);
    }

    @Test
    public void getTranslateOptimizedIAMClient() {
        Translate2Configuration translate2Configuration = new Translate2Configuration();
        translate2Configuration.setUseDefaultCredentialsProvider(true);
        Translate2InternalClient translateClient = Translate2ClientFactory.getTranslateClient(translate2Configuration);
        assertTrue(translateClient instanceof Translate2ClientIAMOptimized);
    }

    @Test
    public void getTranslateSessionTokenClient() {
        Translate2Configuration translate2Configuration = new Translate2Configuration();
        translate2Configuration.setUseSessionCredentials(true);
        Translate2InternalClient translateClient = Translate2ClientFactory.getTranslateClient(translate2Configuration);
        assertTrue(translateClient instanceof Translate2ClientSessionTokenImpl);
    }
}
