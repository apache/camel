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
package org.apache.camel.dataformat.bindy.fix;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.dataformat.bindy.BindyAbstractFactory;
import org.apache.camel.dataformat.bindy.kvp.BindyKeyValuePairDataFormat;
import org.apache.camel.dataformat.bindy.model.fix.simple.Order;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BindyKeyValuePairSeparatorInValueTest {

    @Test
    public void testValueContainingKeyValueSeparator() throws Exception {
        BindyKeyValuePairDataFormat dataFormat = new BindyKeyValuePairDataFormat(Order.class);
        BindyAbstractFactory factory = dataFormat.getFactory();

        // tag 58 (free text) contains '=' which is also the key-value separator
        String message = "8=FIX.4.1" + "" + "58=a=b=c" + "" + "10=220";
        List<String> data = Arrays.asList(message.split("\\u0001"));

        Map<String, Object> model = new HashMap<>();
        model.put(Order.class.getName(), new Order());

        CamelContext camelContext = new DefaultCamelContext();
        factory.bind(camelContext, data, model, 1);

        Order order = (Order) model.get(Order.class.getName());
        assertEquals("a=b=c", order.getText());
    }
}
