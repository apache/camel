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
package org.apache.camel.component.plc4x;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.exceptions.PlcInvalidTagException;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class Plc4XEndpointTest {

    Plc4XEndpoint sut;

    @BeforeEach
    public void setUp() throws Exception {
        Component mockComponent = mock(Component.class, RETURNS_DEEP_STUBS);
        when(mockComponent.getCamelContext()).thenReturn(new DefaultCamelContext());
        sut = new Plc4XEndpoint("plc4x:mock:10.10.10.1/1/1", mockComponent);
    }

    // TODO: figure out what this is
    @Test
    public void createProducer() {
        assertThat(sut.createProducer(), notNullValue());
    }

    @Test
    public void createConsumer() throws Exception {
        assertThat(sut.createConsumer(mock(Processor.class)), notNullValue());
    }

    @Test
    public void isSingleton() {
        assertThat(sut.isSingleton(), is(true));
    }

    @Test
    void doStopClosesALostConnection() throws Exception {
        PlcConnection plcConnectionMock = mock(PlcConnection.class);
        // isConnected() returns false on the mock, and close() fails as on a dead transport
        doThrow(new PlcConnectionException("Transport is not open")).when(plcConnectionMock).close();
        sut.connection = plcConnectionMock;

        sut.doStop();

        verify(plcConnectionMock).close();
        assertThat(sut.connection, nullValue());
    }

    @Test
    void buildPlcReadRequestWithoutAConnection() {
        assertThrows(PlcConnectionException.class, () -> sut.buildPlcReadRequest());
    }

    @Test
    void buildPlcWriteRequestWithoutAConnection() {
        assertThrows(PlcConnectionException.class, () -> sut.buildPlcWriteRequest(Map.of()));
    }

    @Test
    void buildPlcWriteRequestAddsTheAddressAndValueOfEachTag() throws Exception {
        PlcWriteRequest.Builder builder = connectWithWriteRequestBuilder();

        sut.buildPlcWriteRequest(Map.of("temperature", Map.of("%DB1:DINT", 21), "pressure", Map.of("%DB2:DINT", 3)));

        verify(builder).addTagAddress("temperature", "%DB1:DINT", 21);
        verify(builder).addTagAddress("pressure", "%DB2:DINT", 3);
        verify(builder).build();
    }

    @Test
    void buildPlcWriteRequestFailsForATagThatIsNotAMap() throws Exception {
        connectWithWriteRequestBuilder();

        PlcInvalidTagException e
                = assertThrows(PlcInvalidTagException.class, () -> sut.buildPlcWriteRequest(untyped(Map.of("t1", 42))));

        assertThat(e.getMessage(), containsString("Tag t1: the value must be a Map of one address"));
        assertThat(e.getMessage(), containsString("but a java.lang.Integer is"));
    }

    @Test
    void buildPlcWriteRequestFailsForATagWithSeveralAddresses() throws Exception {
        PlcWriteRequest.Builder builder = connectWithWriteRequestBuilder();
        Map<String, Object> addresses = new LinkedHashMap<>();
        addresses.put("%DB1:DINT", 21);
        addresses.put("%DB2:DINT", 3);

        PlcInvalidTagException e
                = assertThrows(PlcInvalidTagException.class, () -> sut.buildPlcWriteRequest(Map.of("t1", addresses)));

        assertThat(e.getMessage(), containsString("but a Map of 2 addresses is"));
        // nothing is written, not even the first address
        verify(builder, never()).build();
    }

    @Test
    void buildPlcWriteRequestFailsForATagWithoutAnAddress() throws Exception {
        connectWithWriteRequestBuilder();

        PlcInvalidTagException e
                = assertThrows(PlcInvalidTagException.class, () -> sut.buildPlcWriteRequest(Map.of("t1", Map.of())));

        assertThat(e.getMessage(), containsString("but a Map without an address is"));
    }

    @Test
    void buildPlcWriteRequestFailsForAnAddressThatIsNotAString() throws Exception {
        connectWithWriteRequestBuilder();

        PlcInvalidTagException e = assertThrows(PlcInvalidTagException.class,
                () -> sut.buildPlcWriteRequest(untyped(Map.of("t1", Map.of(1, 21)))));

        assertThat(e.getMessage(), containsString("Tag t1: the value must be a Map of one address"));
        assertThat(e.getMessage(), containsString("but a Map of 1 address is"));
    }

    private PlcWriteRequest.Builder connectWithWriteRequestBuilder() {
        PlcConnection plcConnectionMock = mock(PlcConnection.class);
        PlcWriteRequest.Builder builder = mock(PlcWriteRequest.Builder.class);
        when(plcConnectionMock.writeRequestBuilder()).thenReturn(builder);
        sut.connection = plcConnectionMock;
        return builder;
    }

    // the body of a message is not checked for its types, so the tags may hold other values
    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> untyped(Map<String, ?> tags) {
        return (Map<String, Map<String, Object>>) (Map<String, ?>) tags;
    }

}
