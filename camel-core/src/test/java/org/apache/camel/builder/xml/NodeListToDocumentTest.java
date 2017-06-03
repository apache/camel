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
package org.apache.camel.builder.xml;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.junit.Ignore;
//import com.sun.org.apache.xerces.internal.dom.ElementNSImpl;

@Ignore("For manual testing CAMEL-6922")
public class NodeListToDocumentTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testXPathNodeResultToDocument() throws Exception {
        // TODO: uses an internal nexus class which can only be tested on some platforms
        /*
        Object result = xpath("/foo").nodeResult().evaluate(createExchange("<foo><bar>1</bar><bar>2</bar></foo>"));
        ElementNSImpl el = assertIsInstanceOf(ElementNSImpl.class, result);
        assertNotNull(el);
        NodeList nodeList = (NodeList) el;
        assertEquals(0, nodeList.getLength());
        Document doc = context.getTypeConverter().convertTo(Document.class, nodeList);
        assertNotNull(doc);
        assertEquals("foo", doc.getFirstChild().getLocalName());
        */
    }

    protected Exchange createExchange(Object xml) {
        Exchange exchange = createExchangeWithBody(context, xml);
        exchange.getIn().setHeader("name", "James");
        return exchange;
    }

}
