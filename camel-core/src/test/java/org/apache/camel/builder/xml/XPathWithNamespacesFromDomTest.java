/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.builder.xml;

import org.apache.camel.TestSupport;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Exchange;
import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.xpath.XPathFunctionResolver;

/**
 * @version $Revision: 534495 $
 */
public class XPathWithNamespacesFromDomTest extends ContextTestSupport {

    public void testXPathUsingDomForNamespaces() throws Exception {
        Document document = context.getTypeConverter().convertTo(Document.class, "<x:foo xmlns:x='n1' xmlns:y='n2'><bar id='a' xmlns:y='n3'/></x:foo>");
        Element element = (Element) document.getElementsByTagName("bar").item(0);
        assertNotNull("Could not find element for id 'a'", element);

        XPathBuilder builder = XPathBuilder.xpath("//y:foo[@id='z']");
        builder.setNamespacesFromDom(element);
        DefaultNamespaceContext namespaceContext = builder.getNamespaceContext();
        assertEquals("y namespace", "n3", namespaceContext.getNamespaceURI("y"));

        assertPredicateMatches(builder, createExchangeWithBody("<blah><foo xmlns='n3' id='z'/></blah>"));
        assertPredicateDoesNotMatch(builder, createExchangeWithBody("<blah><foo xmlns='n2' id='z'/></blah>"));
    }
}
