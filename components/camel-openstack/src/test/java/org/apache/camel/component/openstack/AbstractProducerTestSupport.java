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

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.impl.engine.DefaultHeadersMapFactory;
import org.apache.camel.support.DefaultMessage;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openstack4j.api.OSClient.OSClientV3;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public abstract class AbstractProducerTestSupport {

    @Mock
    protected OSClientV3 client;

    @Mock
    protected Exchange exchange;

    @Mock
    protected ExtendedCamelContext camelContext;

    protected Message msg;

    protected Producer producer;

    @Before
    public void before() throws IOException {
        msg = new DefaultMessage(camelContext);
        when(exchange.getIn()).thenReturn(msg);
        when(camelContext.adapt(ExtendedCamelContext.class)).thenReturn(camelContext);
        when(camelContext.adapt(ExtendedCamelContext.class).getHeadersMapFactory()).thenReturn(new DefaultHeadersMapFactory());
    }
}
