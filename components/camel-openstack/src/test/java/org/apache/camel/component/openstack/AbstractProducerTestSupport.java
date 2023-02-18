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
package org.apache.camel.component.openstack;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.impl.engine.DefaultHeadersMapFactory;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openstack4j.api.OSClient.OSClientV3;

import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class AbstractProducerTestSupport {

    @Mock
    protected OSClientV3 client;

    @Mock
    protected Exchange exchange;

    @Mock(extraInterfaces = ExtendedCamelContext.class)
    protected CamelContext camelContext;

    @Mock
    protected ExtendedCamelContext ecc;

    protected Message msg;

    protected Producer producer;

    @BeforeEach
    public void before() {
        msg = new DefaultMessage(camelContext);
        when(exchange.getIn()).thenReturn(msg);
        when(camelContext.getCamelContextExtension()).thenReturn(ecc);
        when(camelContext.getCamelContextExtension().getHeadersMapFactory()).thenReturn(new DefaultHeadersMapFactory());
    }
}
