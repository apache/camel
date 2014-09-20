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
package org.apache.camel.groovy.converter

import groovy.util.slurpersupport.GPathResult
import org.apache.camel.CamelContext
import org.apache.camel.Exchange
import org.apache.camel.StringSource
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.impl.DefaultExchange
import org.junit.Test
import org.w3c.dom.Node
import org.xml.sax.InputSource

import javax.xml.parsers.DocumentBuilderFactory

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

public class GPathResultConverterTest {
    String xml = "<test><elem1>This is test</elem1></test>"
    CamelContext context = new DefaultCamelContext()

    @Test
    void "should convert string to GPathResult"() {
        Exchange exchange = new DefaultExchange(context)
        exchange.in.setBody(xml, String)
        GPathResult result = exchange.in.getBody(GPathResult)
        checkGPathResult(result)
    }

    @Test
    void "should convert string source to GPathResult"() {
        StringSource input = new StringSource(xml)
        Exchange exchange = new DefaultExchange(context)
        exchange.in.setBody(input, StringSource)
        GPathResult result = exchange.in.getBody(GPathResult)
        checkGPathResult(result)
    }

    @Test
    void "should convert node to GPathResult"() {
        Node node = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)))
        Exchange exchange = new DefaultExchange(context)
        exchange.in.setBody(node, Node)
        GPathResult result = exchange.in.getBody(GPathResult)
        checkGPathResult(result)
    }

    private void checkGPathResult(GPathResult gPathResult) {
        assertNotNull(gPathResult)
        assertEquals(gPathResult.name(), "test")
        assertEquals(gPathResult.elem1.text(), "This is test")
    }
}
