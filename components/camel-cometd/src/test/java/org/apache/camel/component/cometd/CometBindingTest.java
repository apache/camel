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
package org.apache.camel.component.cometd;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.cometd.bayeux.server.ServerMessage;
import org.cometd.bayeux.server.ServerSession;
import org.cometd.server.BayeuxServerImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CometBindingTest {

    private static final Object FOO = new Object();
    private static final Long THIRTY_FOUR = Long.valueOf(34L);
    private static final Double TWO_POINT_ONE = Double.valueOf(2.1);
    private static final Integer EIGHT = new Integer(8);
    private static final String HELLO = "hello";
    private static final String FOO_ATTR_NAME = "foo";
    private static final String LONG_ATTR_NAME = "long";
    private static final String DOUBLE_ATTR_NAME = "double";
    private static final String INTEGER_ATTR_NAME = "integer";
    private static final String STRING_ATTR_NAME = "string";
    private static final String BOOLEAN_ATT_NAME = "boolean";
    private CometdBinding testObj;
    @Mock
    private BayeuxServerImpl bayeux;
    @Mock
    private ServerSession remote;
    @Mock
    private ServerMessage cometdMessage;

    private final CamelContext camelContext = new DefaultCamelContext();

    @Before
    public void before() {
        testObj = new CometdBinding(bayeux);

        Set<String> attributeNames = new HashSet<>(Arrays.asList(STRING_ATTR_NAME, INTEGER_ATTR_NAME,
                                                                       LONG_ATTR_NAME, DOUBLE_ATTR_NAME,
                                                                       FOO_ATTR_NAME, BOOLEAN_ATT_NAME));
        when(remote.getAttributeNames()).thenReturn(attributeNames);
        when(remote.getAttribute(STRING_ATTR_NAME)).thenReturn(HELLO);
        when(remote.getAttribute(INTEGER_ATTR_NAME)).thenReturn(EIGHT);
        when(remote.getAttribute(LONG_ATTR_NAME)).thenReturn(THIRTY_FOUR);
        when(remote.getAttribute(DOUBLE_ATTR_NAME)).thenReturn(TWO_POINT_ONE);
        when(remote.getAttribute(FOO_ATTR_NAME)).thenReturn(FOO);
        when(remote.getAttribute(BOOLEAN_ATT_NAME)).thenReturn(Boolean.TRUE);
        
    }

    @Test
    public void testBindingTransfersSessionAttributtes() {
        // setup
        testObj = new CometdBinding(bayeux, true);

        // act
        Message result = testObj.createCamelMessage(camelContext, remote, cometdMessage, null);

        // assert
        assertEquals(6, result.getHeaders().size());
        assertEquals(HELLO, result.getHeader(STRING_ATTR_NAME));
        assertEquals(EIGHT, result.getHeader(INTEGER_ATTR_NAME));
        assertEquals(THIRTY_FOUR, result.getHeader(LONG_ATTR_NAME));
        assertEquals(TWO_POINT_ONE, result.getHeader(DOUBLE_ATTR_NAME));
        assertEquals(null, result.getHeader(FOO_ATTR_NAME));
        assertTrue((Boolean)result.getHeader(BOOLEAN_ATT_NAME));
    }

    @Test
    public void testBindingHonorsFlagForSessionAttributtes() {
        // act
        Message result = testObj.createCamelMessage(camelContext, remote, cometdMessage, null);

        // assert
        assertEquals(1, result.getHeaders().size());
        assertEquals(null, result.getHeader(STRING_ATTR_NAME));
        assertEquals(null, result.getHeader(INTEGER_ATTR_NAME));
        assertEquals(null, result.getHeader(LONG_ATTR_NAME));
        assertEquals(null, result.getHeader(FOO_ATTR_NAME));
        assertEquals(null, result.getHeader(DOUBLE_ATTR_NAME));
        assertEquals(null, result.getHeader(BOOLEAN_ATT_NAME));
    }

    @Test
    public void testSubscriptionHeadersPassed() {
        // setup
        String expectedSubscriptionInfo = "subscriptionInfo";
        when(cometdMessage.get(CometdBinding.COMETD_SUBSCRIPTION_HEADER_NAME))
            .thenReturn(expectedSubscriptionInfo);

        // act
        Message result = testObj.createCamelMessage(camelContext, remote, cometdMessage, null);

        // assert
        assertEquals(2, result.getHeaders().size());
        assertEquals(expectedSubscriptionInfo,
                     result.getHeader(CometdBinding.COMETD_SUBSCRIPTION_HEADER_NAME));
    }

}

