/**
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
package org.apache.camel.component.rest;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RestProducerTest {

    private CamelContext camelContext = new DefaultCamelContext();

    @Test
    public void shouldCreateDefinedQueryParameters() throws UnsupportedEncodingException, URISyntaxException {
        assertEquals("param=value", RestProducer.createQueryParameters("param=value", null));
    }

    @Test
    public void shouldCreateOptionalPlaceholderQueryParametersForPresentValues()
        throws UnsupportedEncodingException, URISyntaxException {
        final DefaultMessage message = new DefaultMessage(camelContext);
        message.setHeader("param", "header");

        assertEquals("param=header", RestProducer.createQueryParameters("param={param?}", message));
    }

    @Test
    public void shouldCreatePlaceholderQueryParameters() throws UnsupportedEncodingException, URISyntaxException {
        final DefaultMessage message = new DefaultMessage(camelContext);
        message.setHeader("param", "header");

        assertEquals("param=header", RestProducer.createQueryParameters("param={param}", message));
    }

    @Test
    public void shouldCreateQueryNoParameters() throws UnsupportedEncodingException, URISyntaxException {
        assertNull(RestProducer.createQueryParameters(null, null));
    }

    @Test
    public void shouldNotCreateOptionalPlaceholderQueryParametersForMissingValues()
        throws UnsupportedEncodingException, URISyntaxException {
        final DefaultMessage message = new DefaultMessage(camelContext);

        assertEquals("", RestProducer.createQueryParameters("param={param?}", message));
    }

    @Test
    public void shouldSupportAllCombinations() throws UnsupportedEncodingException, URISyntaxException {
        final DefaultMessage message = new DefaultMessage(camelContext);
        message.setHeader("required", "header_required");
        message.setHeader("optional_present", "header_optional_present");

        assertEquals("given=value&required=header_required&optional_present=header_optional_present",
            RestProducer.createQueryParameters(
                "given=value&required={required}&optional={optional?}&optional_present={optional_present?}", message));
    }
}
