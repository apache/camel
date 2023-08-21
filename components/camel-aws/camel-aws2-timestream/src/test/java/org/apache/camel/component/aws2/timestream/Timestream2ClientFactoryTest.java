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
package org.apache.camel.component.aws2.timestream;

import org.apache.camel.component.aws2.timestream.client.Timestream2ClientFactory;
import org.apache.camel.component.aws2.timestream.client.Timestream2InternalClient;
import org.apache.camel.component.aws2.timestream.client.impl.Timestream2ClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.timestream.client.impl.Timestream2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class Timestream2ClientFactoryTest {

    @Test
    public void getStandardTimestreamClientDefault() {
        Timestream2Configuration timestream2Configuration = new Timestream2Configuration();
        Timestream2InternalClient timestreamClient = Timestream2ClientFactory.getTimestreamClient(timestream2Configuration);
        assertTrue(timestreamClient instanceof Timestream2ClientStandardImpl);
    }

    @Test
    public void getStandardTimestreamClient() {
        Timestream2Configuration timestream2Configuration = new Timestream2Configuration();
        timestream2Configuration.setUseDefaultCredentialsProvider(false);
        Timestream2InternalClient timestreamClient = Timestream2ClientFactory.getTimestreamClient(timestream2Configuration);
        assertTrue(timestreamClient instanceof Timestream2ClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedTimestreamClient() {
        Timestream2Configuration timestream2Configuration = new Timestream2Configuration();
        timestream2Configuration.setUseDefaultCredentialsProvider(true);
        Timestream2InternalClient timestream2InternalClient
                = Timestream2ClientFactory.getTimestreamClient(timestream2Configuration);
        assertTrue(timestream2InternalClient instanceof Timestream2ClientIAMOptimizedImpl);
    }
}
