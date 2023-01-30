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

import org.apache.camel.Component;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.plc4x.java.api.PlcConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockito.Mockito.*;

public class Plc4XEndpointTest {

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
    public void doStopBadConnection() throws Exception {
        PlcConnection plcConnectionMock = mock(PlcConnection.class);
        sut.connection = plcConnectionMock;
        doThrow(new RuntimeException("oh noes")).when(plcConnectionMock).close();
        sut.doStop();
    }

}
