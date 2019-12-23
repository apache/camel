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
package org.apache.camel.component.rest;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RestProducerTest {

    private CamelContext camelContext = new DefaultCamelContext();

    @Test
    public void shouldCreateMultipleQueryParameters() throws UnsupportedEncodingException, URISyntaxException {
        final DefaultMessage message = new DefaultMessage(camelContext);
        message.setHeader("multiple", Arrays.asList("value1", "value2", "value3"));

        assertEquals("param=value1&param=value2&param=value3", RestProducer.createQueryParameters("param={multiple}", message));
    }

    @Test
    public void shouldCreateMultipleOptionalQueryParameters() throws UnsupportedEncodingException, URISyntaxException {
        final DefaultMessage message = new DefaultMessage(camelContext);
        message.setHeader("multiple", Collections.EMPTY_LIST);

        assertEquals("", RestProducer.createQueryParameters("multiple={multiple?}", message));
    }

    @Test
    public void shouldCreateDefinedQueryParameters() throws UnsupportedEncodingException, URISyntaxException {
        assertEquals("param=value", RestProducer.createQueryParameters("param=value", null));
    }

    @Test
    public void shouldCreateOptionalPlaceholderQueryParametersForPresentValues() throws UnsupportedEncodingException, URISyntaxException {
        final DefaultMessage message = new DefaultMessage(camelContext);
        message.setHeader("paramPlaceholderName", "paramValue");

        assertEquals("param=paramValue", RestProducer.createQueryParameters("param={paramPlaceholderName?}", message));
    }

    @Test
    public void shouldCreatePlaceholderQueryParameters() throws UnsupportedEncodingException, URISyntaxException {
        final DefaultMessage message = new DefaultMessage(camelContext);
        message.setHeader("paramPlaceholderName", "paramValue");

        assertEquals("param=paramValue", RestProducer.createQueryParameters("param={paramPlaceholderName}", message));
    }

    @Test
    public void shouldCreateQueryNoParameters() throws UnsupportedEncodingException, URISyntaxException {
        assertNull(RestProducer.createQueryParameters(null, null));
    }

    @Test
    public void shouldNotCreateOptionalPlaceholderQueryParametersForMissingValues() throws UnsupportedEncodingException, URISyntaxException {
        final DefaultMessage message = new DefaultMessage(camelContext);

        assertEquals("", RestProducer.createQueryParameters("param={paramPlaceholderName?}", message));
    }

    @Test
    public void shouldSupportAllCombinations() throws UnsupportedEncodingException, URISyntaxException {
        final DefaultMessage message = new DefaultMessage(camelContext);
        message.setHeader("requiredParamPlaceholder", "header_required_value");
        message.setHeader("optionalPresentParamPlaceholder", "header_optional_present_value");
        message.setHeader("multiple", Arrays.asList("value1", "value2", "value3"));
        message.setHeader("multipleOptional", Collections.EMPTY_LIST);

        assertEquals("given=value"
            + "&required=header_required_value"
            + "&optional_present=header_optional_present_value"
            + "&multiple=value1&multiple=value2&multiple=value3",
            RestProducer.createQueryParameters("given=value"
                + "&required={requiredParamPlaceholder}"
                + "&optional={optionalParamPlaceholder?}"
                + "&optional_present={optionalPresentParamPlaceholder?}"
                + "&multiple={multiple}"
                + "&multipleOptional={multipleOptional?}",
                message));
    }
}
