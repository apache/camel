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
package org.apache.camel.component.aws2.sns;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SnsProducerTest {

    @Mock
    private Exchange exchange;
    @Mock
    private Sns2Endpoint endpoint;
    private Sns2Producer producer;

    @BeforeEach
    public void setUp() {
        producer = new Sns2Producer(endpoint);

        when(endpoint.getHeaderFilterStrategy()).thenReturn(new Sns2HeaderFilterStrategy());
    }

    @Test
    public void translateStringArrayEscapesJsonCharacters() throws Exception {
        List<String> values = List.of("a\"b", "C:\\temp", "line\nbreak", "tab\tvalue", "\b\f\r\u0001", "caf\u00e9",
                "application/json", "\u007f", "\u2000");

        MessageAttributeValue attribute = producer.translateAttributes(Map.of("values", values), exchange).get("values");

        assertThat(attribute.dataType(), is("String.Array"));
        assertThat(Jsoner.deserialize(attribute.stringValue()), is(values));
    }

    @Test
    public void translateStringArrayQuotesOtherMemberTypes() throws Exception {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        Date date = new Date(0);
        List<Object> values = List.of(Membership.GOLD, 'x', id, date);

        MessageAttributeValue attribute = producer.translateAttributes(Map.of("values", values), exchange).get("values");

        assertThat(Jsoner.deserialize(attribute.stringValue()), is(List.of("GOLD", "x", id.toString(), date.toString())));
    }

    @Test
    public void translateStringArrayQuotesNonFiniteNumbers() throws Exception {
        List<Number> values = List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);

        MessageAttributeValue attribute = producer.translateAttributes(Map.of("values", values), exchange).get("values");

        assertThat(Jsoner.deserialize(attribute.stringValue()),
                is(List.of("NaN", "Infinity", "-Infinity", "NaN", "Infinity", "-Infinity")));
    }

    @Test
    public void translateStringArrayKeepsJsonPrimitives() {
        List<Object> values = Arrays.asList(12, 1.5D, 2.5F, true, null);

        MessageAttributeValue attribute = producer.translateAttributes(Map.of("values", values), exchange).get("values");

        assertThat(attribute.stringValue(), is("[12, 1.5, 2.5, true, null]"));
    }

    private enum Membership {
        GOLD
    }

    @Test
    public void translateAttributes() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("key1", null);
        headers.put("key2", "");
        headers.put("key3", "value3");
        headers.put("key4", Arrays.asList("Value4", "Value5", "Value6"));
        headers.put("key5", Arrays.asList("Value7", null, "Value9"));
        headers.put("key6", Arrays.asList(10, null, 12));
        headers.put("key7", Arrays.asList(true, null, false));
        headers.put("key8", 42);

        Map<String, MessageAttributeValue> translateAttributes = producer.translateAttributes(headers, exchange);

        assertThat(translateAttributes.size(), is(6));
        assertThat(translateAttributes.get("key3").dataType(), is("String"));
        assertThat(translateAttributes.get("key3").stringValue(), is("value3"));
        assertThat(translateAttributes.get("key4").dataType(), is("String.Array"));
        assertThat(translateAttributes.get("key4").stringValue(), is("[\"Value4\", \"Value5\", \"Value6\"]"));
        assertThat(translateAttributes.get("key5").stringValue(), is("[\"Value7\", null, \"Value9\"]"));
        assertThat(translateAttributes.get("key6").stringValue(), is("[10, null, 12]"));
        assertThat(translateAttributes.get("key7").stringValue(), is("[true, null, false]"));
        assertThat(translateAttributes.get("key8").dataType(), is("String"));
        assertThat(translateAttributes.get("key8").stringValue(), is("42"));
    }
}
