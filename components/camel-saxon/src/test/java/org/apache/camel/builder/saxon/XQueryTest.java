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
package org.apache.camel.builder.saxon;

import org.w3c.dom.Document;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.xquery.XQueryBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.xquery.XQueryBuilder.xquery;
import static org.apache.camel.util.ObjectHelper.className;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XQueryTest {
    @Test
    public void testXQuery() {
        CamelContext context = new DefaultCamelContext();
        context.start();

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(
                "<products><product type='food'><pizza/></product><product type='beer'><stella/></product></products>");

        XQueryBuilder xquery = xquery(".//product[@type = 'beer']/*");
        xquery.init(context);

        Object result = xquery.evaluate(exchange, Object.class);
        assertTrue(result instanceof Document, "Should be a document but was: " + className(result));
        Document doc = (Document) result;
        assertEquals("stella", doc.getDocumentElement().getLocalName(), "Root document element name");

        result = xquery.evaluate(exchange, String.class);
        assertEquals("<stella/>", result, "Get a wrong result");
    }
}
