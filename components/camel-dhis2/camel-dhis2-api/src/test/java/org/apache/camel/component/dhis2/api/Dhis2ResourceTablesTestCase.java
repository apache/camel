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
package org.apache.camel.component.dhis2.api;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.hisp.dhis.integration.sdk.api.Dhis2Response;
import org.hisp.dhis.integration.sdk.api.operation.PostOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class Dhis2ResourceTablesTestCase {

    @Mock
    private Dhis2Client dhis2Client;

    @Mock
    private PostOperation postOperation;

    @BeforeEach
    public void beforeEach() {
        when(dhis2Client.post(any())).thenReturn(postOperation);
        when(postOperation.withParameter(any(), any())).thenReturn(postOperation);
        when(postOperation.transfer()).thenReturn(new Dhis2Response() {
            @Override
            public <T> T returnAs(Class<T> responseType) {
                return (T) Map.of("response", Map.of("id", UUID.randomUUID().toString()));
            }

            @Override
            public InputStream read() {
                return new ByteArrayInputStream(new byte[]{});
            }

            @Override
            public void close() {

            }

            @Override
            public String getUrl() {
                return "";
            }
        });
    }

    @Test
    @Timeout(5)
    public void testAnalyticsDoesNotBlockGivenAsyncIsTrue() {
        Dhis2ResourceTables dhis2ResourceTables = new Dhis2ResourceTables(dhis2Client);
        dhis2ResourceTables.analytics(ThreadLocalRandom.current().nextBoolean(), ThreadLocalRandom.current().nextBoolean(),
                ThreadLocalRandom.current().nextInt(), ThreadLocalRandom.current().nextInt(), true);
    }
}
