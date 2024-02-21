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
import org.hisp.dhis.integration.sdk.api.operation.DeleteOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class Dhis2DeleteTestCase {
    @Mock
    private Dhis2Client dhis2Client;

    @Mock
    private DeleteOperation deleteOperation;

    @BeforeEach
    public void beforeEach() {
        when(dhis2Client.delete(any())).thenReturn(deleteOperation);
        when(deleteOperation.withParameter(any(), any())).thenReturn(deleteOperation);
        when(deleteOperation.transfer()).thenReturn(new Dhis2Response() {
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
    public void testResourceGivenMapOfListsQueryParams() {
        Dhis2Delete dhis2Delete = new Dhis2Delete(dhis2Client);
        dhis2Delete.resource(null, null, Map.of("foo", List.of("bar")));
    }

    @Test
    public void testResourceGivenMapOfStringsQueryParams() {
        Dhis2Delete dhis2Delete = new Dhis2Delete(dhis2Client);
        dhis2Delete.resource(null, null, Map.of("foo", "bar"));
    }
}
