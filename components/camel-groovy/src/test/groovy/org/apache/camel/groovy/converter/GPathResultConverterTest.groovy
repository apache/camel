package org.apache.camel.groovy.converter

import groovy.util.slurpersupport.GPathResult
import org.apache.camel.CamelContext
import org.apache.camel.Exchange
import org.apache.camel.StringSource
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.impl.DefaultExchange
import org.junit.Test
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

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
