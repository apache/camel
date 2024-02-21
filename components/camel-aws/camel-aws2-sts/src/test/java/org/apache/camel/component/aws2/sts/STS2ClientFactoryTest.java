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
package org.apache.camel.component.aws2.sts;

import org.apache.camel.component.aws2.sts.client.STS2ClientFactory;
import org.apache.camel.component.aws2.sts.client.STS2InternalClient;
import org.apache.camel.component.aws2.sts.client.impl.STS2ClientIAMOptimized;
import org.apache.camel.component.aws2.sts.client.impl.STS2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class STS2ClientFactoryTest {

    @Test
    public void getStandardSTSClientDefault() {
        STS2Configuration sts2Configuration = new STS2Configuration();
        STS2InternalClient stsClient = STS2ClientFactory.getStsClient(sts2Configuration);
        assertTrue(stsClient instanceof STS2ClientStandardImpl);
    }

    @Test
    public void getStandardSTSClient() {
        STS2Configuration sts2Configuration = new STS2Configuration();
        sts2Configuration.setUseDefaultCredentialsProvider(false);
        STS2InternalClient stsClient = STS2ClientFactory.getStsClient(sts2Configuration);
        assertTrue(stsClient instanceof STS2ClientStandardImpl);
    }

    @Test
    public void getSTSOptimizedIAMClient() {
        STS2Configuration sts2Configuration = new STS2Configuration();
        sts2Configuration.setUseDefaultCredentialsProvider(true);
        STS2InternalClient stsClient = STS2ClientFactory.getStsClient(sts2Configuration);
        assertTrue(stsClient instanceof STS2ClientIAMOptimized);
    }
}
