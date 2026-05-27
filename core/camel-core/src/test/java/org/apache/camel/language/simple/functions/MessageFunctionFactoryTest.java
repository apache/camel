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

import org.apache.camel.language.simple.MyAttachmentMessage;
import org.apache.camel.spi.SimpleLanguageFunctionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MessageFunctionFactoryTest extends AbstractSimpleFunctionFactoryTestSupport {

    @Override
    protected SimpleLanguageFunctionFactory createFactory() {
        return new MessageFunctionFactory();
    }

    // --- messageAs( ---

    @Test
    public void testMessageAs() {
        // exchange.getMessage(MyAttachmentMessage.class) returns null when the current message is
        // a DefaultMessage (no type-converter exists). messageOgnlExpression short-circuits on null
        // and returns null without invoking OGNL. The original SimpleTest used assertPredicate(...,
        // false) which passed because the Simple predicate engine coerces null to false; the raw
        // expression value itself is null, not Boolean.FALSE.
        assertNull(evaluate("messageAs(org.apache.camel.language.simple.MyAttachmentMessage).hasAttachments",
                Boolean.class));
        assertNull(evaluate("messageAs(org.apache.camel.language.simple.MyAttachmentMessage)?.hasAttachments",
                Boolean.class));

        MyAttachmentMessage msg = new MyAttachmentMessage(exchange);
        msg.setBody("<hello id='m123'>world!</hello>");
        exchange.setMessage(msg);

        assertEquals(true,
                evaluate("messageAs(org.apache.camel.language.simple.MyAttachmentMessage).hasAttachments", Boolean.class));
        assertEquals(true,
                evaluate("messageAs(org.apache.camel.language.simple.MyAttachmentMessage)?.hasAttachments", Boolean.class));
        assertEquals("42",
                evaluate("messageAs(org.apache.camel.language.simple.MyAttachmentMessage).size", String.class));
    }
}
