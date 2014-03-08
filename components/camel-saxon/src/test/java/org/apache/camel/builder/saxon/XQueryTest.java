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
package org.apache.camel.builder.saxon;

import org.w3c.dom.Document;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Assert;
import org.junit.Test;

import static org.apache.camel.component.xquery.XQueryBuilder.xquery;
import static org.apache.camel.util.ObjectHelper.className;

/**
 * @version 
 */
public class XQueryTest extends Assert {
    @Test
    public void testXQuery() throws Exception {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setBody("<products><product type='food'><pizza/></product><product type='beer'><stella/></product></products>");

        Object result = xquery(".//product[@type = 'beer']/*").evaluate(exchange, Object.class);

        assertTrue("Should be a document but was: " + className(result), result instanceof Document);

        Document doc = (Document) result;
        assertEquals("Root document element name", "stella", doc.getDocumentElement().getLocalName());
        
        result = xquery(".//product[@type = 'beer']/*").evaluate(exchange, String.class);
        assertEquals("Get a wrong result", "<stella/>", result);
    }
}
