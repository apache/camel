/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
        };
        when(getOperation.transfer()).thenReturn(dhis2Response);
        Dhis2Get dhis2Get = new Dhis2Get(dhis2Client);
        dhis2Get.resource(null, null, null, Map.of("foo", List.of("bar")));
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
        };
        when(getOperation.transfer()).thenReturn(dhis2Response);
        Dhis2Get dhis2Get = new Dhis2Get(dhis2Client);
        dhis2Get.resource(null, null, null, Map.of("foo", "bar"));
        verify(getOperation, times(1)).withParameter("foo", "bar");
    }

    @Test
    public void testCollectionGivenMapOfStringsQueryParams() {
        DefaultPagingCollectOperation defaultPagingCollectOperation = new DefaultPagingCollectOperation(
                "https://play.dhis2.org/2.39.0.1", "", null, new JacksonConverterFactory(), getOperation);

        Dhis2Response dhis2Response = new Dhis2Response() {
            @Override
            public <T> T returnAs(Class<T> responseType) {
                Page page = new Page();
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
        };
        when(getOperation.transfer()).thenReturn(dhis2Response);
        when(getOperation.withPaging()).thenReturn(
                new DefaultPagingCollectOperation(
                        "https://play.dhis2.org/2.39.0.1", "", null,
                        new JacksonConverterFactory(), getOperation));

        Dhis2Get dhis2Get = new Dhis2Get(dhis2Client);
        dhis2Get.collection("bunnies", null, null, null, null, Map.of("foo", "bar"));
        verify(getOperation, times(1)).withParameter("foo", "bar");
    }
}
