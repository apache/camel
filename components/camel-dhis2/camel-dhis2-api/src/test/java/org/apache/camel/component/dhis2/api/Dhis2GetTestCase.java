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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hisp.dhis.api.model.Page;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;
import org.hisp.dhis.integration.sdk.api.Dhis2Response;
import org.hisp.dhis.integration.sdk.api.operation.GetOperation;
import org.hisp.dhis.integration.sdk.internal.converter.JacksonConverterFactory;
import org.hisp.dhis.integration.sdk.internal.operation.DefaultSimpleCollectOperation;
import org.hisp.dhis.integration.sdk.internal.operation.page.DefaultPagingCollectOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class Dhis2GetTestCase {
    @Mock
    private Dhis2Client dhis2Client;

    @Mock
    private GetOperation getOperation;

    @BeforeEach
    public void beforeEach() {
        when(dhis2Client.get(any())).thenReturn(getOperation);
        when(getOperation.withParameter(any(), any())).thenReturn(getOperation);
    }

    @Test
    public void testResourceGivenMapOfListsQueryParams() {
        Dhis2Response dhis2Response = new Dhis2Response() {
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
        };
        when(getOperation.withParameter(any(), any())).thenReturn(getOperation);
        when(getOperation.transfer()).thenReturn(dhis2Response);
        Dhis2Get dhis2Get = new Dhis2Get(dhis2Client);
        dhis2Get.resource(null, null, null, null, Map.of("foo", List.of("bar")));
        verify(getOperation, times(1)).withParameter("foo", "bar");
    }

    @Test
    public void testResourceGivenMapOfStringsQueryParams() {
        Dhis2Response dhis2Response = new Dhis2Response() {
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
        };
        when(getOperation.withParameter(any(), any())).thenReturn(getOperation);
        when(getOperation.transfer()).thenReturn(dhis2Response);
        Dhis2Get dhis2Get = new Dhis2Get(dhis2Client);
        dhis2Get.resource(null, null, null, null, Map.of("foo", "bar"));
        verify(getOperation, times(1)).withParameter("foo", "bar");
    }

    @Test
    public void testResourceGivenOrRootJunction() {
        Dhis2Response dhis2Response = new Dhis2Response() {
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
        };
        when(getOperation.withParameter(any(), any())).thenReturn(getOperation);
        when(getOperation.transfer()).thenReturn(dhis2Response);
        Dhis2Get dhis2Get = new Dhis2Get(dhis2Client);
        dhis2Get.resource(null, null, null, RootJunctionEnum.OR, null);
        verify(getOperation, times(1)).withOrRootJunction();
    }

    @Test
    public void testResourceGivenAndRootJunction() {
        Dhis2Response dhis2Response = new Dhis2Response() {
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
        };
        when(getOperation.withParameter(any(), any())).thenReturn(getOperation);
        when(getOperation.transfer()).thenReturn(dhis2Response);
        Dhis2Get dhis2Get = new Dhis2Get(dhis2Client);
        dhis2Get.resource(null, null, null, RootJunctionEnum.AND, null);
        verify(getOperation, times(1)).withAndRootJunction();
    }

    @Test
    public void testCollectionGivenPagingIsTrue() {
        Dhis2Response dhis2Response = new Dhis2Response() {
            @Override
            public <T> T returnAs(Class<T> responseType) {
                Page page = new Page(1, 50);
                page.setAdditionalProperty("bunnies", new ArrayList<>());

                return (T) page;
            }

            @Override
            public InputStream read() {
                return new ByteArrayInputStream(new byte[] {});
            }

            @Override
            public void close()
                    throws IOException {

            }

            @Override
            public String getUrl() {
                return "";
            }
        };
        when(getOperation.withParameter(any(), any())).thenReturn(getOperation);
        when(getOperation.withParameter(any(), any())).thenReturn(getOperation);
        when(getOperation.transfer()).thenReturn(dhis2Response);
        when(getOperation.withPaging()).thenReturn(
                new DefaultPagingCollectOperation(
                        "https://play.dhis2.org/2.39.0.1", "", null, new JacksonConverterFactory(), getOperation));

        Dhis2Get dhis2Get = new Dhis2Get(dhis2Client);
        dhis2Get.collection("bunnies", "bunnies", true, null, null, null, Map.of("foo", "bar"));
        verify(getOperation, times(1)).withParameter("foo", "bar");
    }

    @Test
    public void testCollectionGivenMapOfStringsQueryParams() {
        Dhis2Response dhis2Response = new Dhis2Response() {
            @Override
            public <T> T returnAs(Class<T> responseType) {
                return (T) Map.of("bunnies", new ArrayList<>());
            }

            @Override
            public InputStream read() {
                return new ByteArrayInputStream(new byte[] {});
            }

            @Override
            public void close()
                    throws IOException {

            }

            @Override
            public String getUrl() {
                return "";
            }
        };
        when(getOperation.withParameter(any(), any())).thenReturn(getOperation);
        when(getOperation.withParameter(any(), any())).thenReturn(getOperation);
        when(getOperation.transfer()).thenReturn(dhis2Response);
        when(getOperation.withoutPaging()).thenReturn(
                new DefaultSimpleCollectOperation(
                        "https://play.dhis2.org/2.39.0.1", "", null, new JacksonConverterFactory(), getOperation));

        Dhis2Get dhis2Get = new Dhis2Get(dhis2Client);
        dhis2Get.collection("bunnies", "bunnies", null, null, null, null, Map.of("foo", "bar"));
        verify(getOperation, times(1)).withParameter("foo", "bar");
    }

    @Test
    public void testCollectionGivenOrRootJunction() {
        Dhis2Response dhis2Response = new Dhis2Response() {
            @Override
            public <T> T returnAs(Class<T> responseType) {
                return (T) Map.of("bunnies", new ArrayList<>());
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
        };
        when(getOperation.withParameter(any(), any())).thenReturn(getOperation);
        when(getOperation.transfer()).thenReturn(dhis2Response);
        when(getOperation.withoutPaging()).thenReturn(
                new DefaultSimpleCollectOperation(
                        "https://play.dhis2.org/2.39.0.1", "", null,
                        new JacksonConverterFactory(), getOperation));

        Dhis2Get dhis2Get = new Dhis2Get(dhis2Client);
        dhis2Get.collection("bunnies", "bunnies", null, null, null, RootJunctionEnum.OR, null);
        verify(getOperation, times(1)).withOrRootJunction();
    }

    @Test
    public void testCollectionGivenAndRootJunction() {
        Dhis2Response dhis2Response = new Dhis2Response() {
            @Override
            public <T> T returnAs(Class<T> responseType) {
                return (T) Map.of("bunnies", new ArrayList<>());
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
        };
        when(getOperation.withParameter(any(), any())).thenReturn(getOperation);
        when(getOperation.transfer()).thenReturn(dhis2Response);
        when(getOperation.withoutPaging()).thenReturn(new DefaultSimpleCollectOperation(
                "https://play.dhis2.org/2.39.0.1", "", null, new JacksonConverterFactory(), getOperation));

        Dhis2Get dhis2Get = new Dhis2Get(dhis2Client);
        dhis2Get.collection("bunnies", "bunnies", null, null, null, RootJunctionEnum.AND, null);
        verify(getOperation, times(1)).withAndRootJunction();
    }

    @Test
    public void testCollectionGivenMultipleFilters() {
        Dhis2Response dhis2Response = new Dhis2Response() {
            public <T> T returnAs(Class<T> responseType) {
                return (T) Map.of("bunnies", new ArrayList<>());
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
        };
        when(getOperation.withParameter(any(), any())).thenReturn(getOperation);
        when(getOperation.transfer()).thenReturn(dhis2Response);
        when(getOperation.withoutPaging()).thenReturn(
                new DefaultSimpleCollectOperation(
                        "https://play.dhis2.org/2.39.0.1", "", null,
                        new JacksonConverterFactory(), getOperation));

        Dhis2Get dhis2Get = new Dhis2Get(dhis2Client);
        dhis2Get.collection("bunnies", "bunnies", null, null, List.of("id:in:[id1,id2]", "code:eq:code1"), null, null);
        verify(getOperation, times(1)).withFilter("id:in:[id1,id2]");
        verify(getOperation, times(1)).withFilter("code:eq:code1");
    }

}
