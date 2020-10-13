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
package org.apache.camel.component.aws.ddbstream;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DdbStreamEndpointTest {

    private CamelContext context;

    @Mock
    private SequenceNumberProvider sequenceNumberProvider;
    @Mock
    private AmazonDynamoDBStreams amazonDynamoDBStreams;

    @BeforeEach
    public void setup() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.bind("someSeqNumProv", sequenceNumberProvider);
        registry.bind("ddbStreamsClient", amazonDynamoDBStreams);

        context = new DefaultCamelContext(registry);
    }

    @Test
    public void itExtractsTheSequenceNumber() throws Exception {
        when(sequenceNumberProvider.getSequenceNumber()).thenReturn("seq");

        DdbStreamEndpoint undertest = (DdbStreamEndpoint) context.getEndpoint("aws-ddbstream://table"
                                                                              + "?amazonDynamoDbStreamsClient=#ddbStreamsClient"
                                                                              + "&iteratorType=AFTER_SEQUENCE_NUMBER"
                                                                              + "&sequenceNumberProvider=#someSeqNumProv");

        assertEquals("seq", undertest.getSequenceNumber());
    }

    @Test
    public void itExtractsTheSequenceNumberFromALiteralString() throws Exception {

        DdbStreamEndpoint undertest = (DdbStreamEndpoint) context.getEndpoint("aws-ddbstream://table"
                                                                              + "?amazonDynamoDbStreamsClient=#ddbStreamsClient"
                                                                              + "&iteratorType=AFTER_SEQUENCE_NUMBER"
                                                                              + "&sequenceNumberProvider=seq");

        assertEquals("seq", undertest.getSequenceNumber());
    }

    @Test
    public void onSequenceNumberAgnosticIteratorsTheProviderIsIgnored() throws Exception {
        DdbStreamEndpoint undertest = (DdbStreamEndpoint) context.getEndpoint("aws-ddbstream://table"
                                                                              + "?amazonDynamoDbStreamsClient=#ddbStreamsClient"
                                                                              + "&iteratorType=LATEST"
                                                                              + "&sequenceNumberProvider=#someSeqNumProv");

        assertEquals("", undertest.getSequenceNumber());
        verify(sequenceNumberProvider, never()).getSequenceNumber();
    }

    @Test
    public void sequenceNumberFetchingThrowsSomethingUsefulIfMisconfigurered() throws Exception {
        DdbStreamEndpoint undertest = (DdbStreamEndpoint) context.getEndpoint("aws-ddbstream://table"
                                                                              + "?amazonDynamoDbStreamsClient=#ddbStreamsClient"
                                                                              + "&iteratorType=AT_SEQUENCE_NUMBER"); // NOTE: missing sequence number provider parameter

        try {
            undertest.getSequenceNumber();
            fail("Expected exception");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("sequenceNumberProvider"));
        }
    }
}
