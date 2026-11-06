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
package org.apache.camel.component.couchdb;

import com.ibm.cloud.cloudant.v1.Cloudant;
import com.ibm.cloud.cloudant.v1.model.PostChangesOptions;
import com.ibm.cloud.sdk.core.http.ServiceCall;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * maxMessagesPerPoll is sent as the limit of the _changes request, and 0 or a negative number means no limit.
 */
public class CouchDbClientWrapperTest {

    @ParameterizedTest
    @CsvSource({ "10, 10", "1, 1", "0,", "-1,", "-5," })
    @SuppressWarnings("unchecked")
    void testPollChangesLimit(long maxMessagesPerPoll, Long expectedLimit) {
        Cloudant cloudant = mock(Cloudant.class);
        when(cloudant.postChanges(any())).thenReturn(mock(ServiceCall.class));
        CouchDbClientWrapper wrapper = new CouchDbClientWrapper(cloudant, "db", false);

        wrapper.pollChanges("main_only", "now", 30000, maxMessagesPerPoll);

        ArgumentCaptor<PostChangesOptions> options = ArgumentCaptor.forClass(PostChangesOptions.class);
        verify(cloudant).postChanges(options.capture());
        assertEquals("db", options.getValue().db());
        assertEquals("now", options.getValue().since());
        assertEquals(expectedLimit, options.getValue().limit());
    }
}
