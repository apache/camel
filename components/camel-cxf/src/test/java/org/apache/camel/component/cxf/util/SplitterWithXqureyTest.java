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

package org.apache.camel.component.cxf.util;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.org.apache.xerces.internal.dom.ElementImpl;
import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.staxutils.W3CDOMStreamWriter;


public class SplitterWithXqureyTest extends ContextTestSupport {
    private static String xmlData = "<workflow id=\"12345\" xmlns=\"http://camel.apache.org/schema/one\" "
        + "xmlns:two=\"http://camel.apache.org/schema/two\">"
        + "<person><name>Willem</name></person> "
        + "<other><two:test name=\"123\">One</two:test></other>"
        + "<other><two:test name=\"456\">Two</two:test></other>"
        + "<other><test>Three</test></other>"
        + "<other><test>Foure</test></other></workflow>";
    private static String[] verifyStrings = new String[] {
        "<other xmlns=\"http://camel.apache.org/schema/one\" xmlns:two=\"http://camel.apache.org/schema/two\"><two:test name=\"123\">One</two:test></other>",
        "<other xmlns=\"http://camel.apache.org/schema/one\" xmlns:two=\"http://camel.apache.org/schema/two\"><two:test name=\"456\">Two</two:test></other>",
        "<other xmlns=\"http://camel.apache.org/schema/one\"><test>Three</test></other>",
        "<other xmlns=\"http://camel.apache.org/schema/one\"><test>Foure</test></other>"
    };
        
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // split the message with namespaces defined 
                Namespaces namespaces = new Namespaces("one", "http://camel.apache.org/schema/one");                
                from("direct:endpoint").splitter().xpath("//one:other", namespaces).to("mock:result");
            }
        };
    }
    
    public void testSenderXmlData() throws Exception {        
        MockEndpoint result = getMockEndpoint("mock:result");
        result.reset();
        result.expectedMessageCount(4);
        template.sendBody("direct:endpoint", xmlData);
        assertMockEndpointsSatisfied();
        int i = 0;
        for (Exchange exchange : result.getExchanges()) {
            ElementImpl element = (ElementImpl) exchange.getIn().getBody();           
            String message = CxfUtils.elementToString(element);            
            log.info("The splited message is " + message);
            assertTrue("The splitted message should start with <other", message.indexOf("<other") == 0);
            assertEquals("Get a wrong message", verifyStrings[i], message);
            i++;
        }
    }
    
   

}
