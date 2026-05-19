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
package org.apache.camel.language.simple.functions;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JoinFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new JoinFunctionFactory();
    }

    @Test
    public void testJoinBody() {
        List<Object> data = new ArrayList<>();
        data.add("A");
        data.add("B");
        data.add("C");
        exchange.getIn().setBody(data);

        assertEquals("A,B,C", evaluate("join()", String.class));
        assertEquals("A;B;C", evaluate("join(;)", String.class));
        assertEquals("A B C", evaluate("join(' ')", String.class));
        assertEquals("id=A,id=B,id=C", evaluate("join(',','id=')", String.class));
        assertEquals("id=A&id=B&id=C", evaluate("join(&,id=)", String.class));
    }

    @Test
    public void testJoinHeader() {
        List<Object> data = new ArrayList<>();
        data.add("A");
        data.add("B");
        data.add("C");
        exchange.getIn().setHeader("id", data);

        assertEquals("id=A&id=B&id=C", evaluate("join('&','id=','${header.id}')", String.class));
    }

    @Test
    public void testCreateCode() {
        assertEquals("var val = body;\n        return join(exchange, val, \",\", null);", createCode("join()"));
    }
}
