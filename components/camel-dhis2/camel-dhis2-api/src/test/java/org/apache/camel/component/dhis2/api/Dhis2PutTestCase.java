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
import java.util.List;
import java.util.Map;

import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.hisp.dhis.integration.sdk.api.Dhis2Response;
import org.hisp.dhis.integration.sdk.api.operation.PutOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class Dhis2PutTestCase {
    @Mock
    private Dhis2Client dhis2Client;

    @Mock
    private PutOperation putOperation;

    @BeforeEach
    public void beforeEach() {
        when(dhis2Client.put(any())).thenReturn(putOperation);
        when(putOperation.withParameter(any(), any())).thenReturn(putOperation);
        when(putOperation.transfer()).thenReturn(new Dhis2Response() {
            @Override
            public <T> T returnAs(Class<T> responseType) {
                return null;
            }

            @Override
            public InputStream read() {
                return new ByteArrayInputStream(new byte[] {});
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
    public void testResourceGivenMapOfListsQueryParams() throws Exception {
        Dhis2Put dhis2Put = new Dhis2Put(dhis2Client);
        InputStream result = dhis2Put.resource(null, null, Map.of("foo", List.of("bar")));
        assertNotNull(result, "resource() should return a non-null InputStream");
        assertEquals(0, result.readAllBytes().length, "response body should be empty");
        verify(dhis2Client).put(any());
    }

    @Test
    public void testResourceGivenMapOfStringsQueryParams() throws Exception {
        Dhis2Put dhis2Put = new Dhis2Put(dhis2Client);
        InputStream result = dhis2Put.resource(null, null, Map.of("foo", "bar"));
        assertNotNull(result, "resource() should return a non-null InputStream");
        assertEquals(0, result.readAllBytes().length, "response body should be empty");
        verify(dhis2Client).put(any());
    }
}
