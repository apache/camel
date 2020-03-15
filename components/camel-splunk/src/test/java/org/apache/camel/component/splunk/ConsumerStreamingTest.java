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
package org.apache.camel.component.splunk;

import java.io.InputStream;
import java.util.Map;

import com.splunk.Job;
import com.splunk.JobCollection;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.splunk.event.SplunkEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConsumerStreamingTest extends SplunkMockTestSupport {

    @Mock
    Job jobMock;

    @Mock
    JobCollection jobCollection;

    @Test
    public void testSearch() throws Exception {
        MockEndpoint searchMock = getMockEndpoint("mock:search-result");
        searchMock.expectedMessageCount(3);

        when(service.getJobs()).thenReturn(jobCollection);
        when(jobCollection.create(anyString(), any())).thenReturn(jobMock);
        when(jobMock.isDone()).thenReturn(Boolean.TRUE);
        InputStream stream = ConsumerTest.class.getResourceAsStream("/resultsreader_test_data.json");
        when(jobMock.getResults(any())).thenReturn(stream);

        assertMockEndpointsSatisfied();
        SplunkEvent recieved = searchMock.getReceivedExchanges().get(0).getIn().getBody(SplunkEvent.class);
        assertNotNull(recieved);
        Map<String, String> data = recieved.getEventData();
        assertEquals("indexertpool", data.get("name"));
        stream.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("splunk://normal?delay=5000&username=foo&password=bar&initEarliestTime=-10s&latestTime=now&search=search index=myindex&sourceType=testSource&streaming=true")
                        .to("mock:search-result");
            }
        };
    }
}

