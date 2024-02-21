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
package org.apache.camel.builder.xml;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.language.xpath.XPathBuilder;
import org.apache.camel.util.StringHelper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

public class XPathTransformTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testXPathTransform() throws Exception {
        Document doc = context.getTypeConverter().convertTo(Document.class,
                "<root><firstname>Apache</firstname><lastname>Camel</lastname></root>");
        NodeList list = XPathBuilder.xpath("/root/firstname", NodeList.class).evaluate(context, doc, NodeList.class);
        assertNotNull(list);
        list.item(0).setTextContent("Servicemix");

        String out = context.getTypeConverter().convertTo(String.class, doc);
        assertEquals("<root><firstname>Servicemix</firstname><lastname>Camel</lastname></root>", out);
    }

    @Test
    public void testXPathNamespaceLoggingEnabledJavaDSL() throws Exception {
        assumeThat("Reflection on java.lang.Field has been disabled in JDK 12", getJavaVersion(),
                Matchers.lessThanOrEqualTo(11));

        Logger l = mock(Logger.class);

        when(l.isInfoEnabled()).thenReturn(true);

        String body
                = "<aRoot xmlns:nsa=\"http://namespacec.net\"><nsa:a xmlns:nsa=\"http://namespacea.net\">Hello|there|Camel</nsa:a>"
                  + "<nsb:a xmlns:nsb=\"http://namespaceb.net\">Hello|there|Camel</nsb:a><nsb:a xmlns:nsb=\"http://namespaceb.net\">Hello|there|Camel</nsb:a>"
                  + "<a xmlns=\"http://defaultNamespace.net\">Hello|there|Camel</a><a>Hello|there|Camel</a></aRoot>";
        Document doc = context.getTypeConverter().convertTo(Document.class, body);
        Field logField = XPathBuilder.class.getDeclaredField("LOG");
        logField.setAccessible(true);

        Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(logField, logField.getModifiers() & ~Modifier.FINAL);

        logField.set(null, l);

        NodeList list = XPathBuilder.xpath("//*", NodeList.class).logNamespaces().evaluate(context, doc, NodeList.class);
        assertNotNull(list);

        verify(l).info(argThat(containsString("Namespaces discovered in message")), any(Object.class));
    }

    @Test
    public void testXPathNamespaceLoggingDisabledJavaDSL() throws Exception {
        assumeThat("Reflection on java.lang.Field has been disabled in JDK 12", getJavaVersion(),
                Matchers.lessThanOrEqualTo(11));

        Logger l = mock(Logger.class);

        when(l.isInfoEnabled()).thenReturn(true);

        String body
                = "<aRoot xmlns:nsa=\"http://namespacec.net\"><nsa:a xmlns:nsa=\"http://namespacea.net\">Hello|there|Camel</nsa:a>"
                  + "<nsb:a xmlns:nsb=\"http://namespaceb.net\">Hello|there|Camel</nsb:a><nsb:a xmlns:nsb=\"http://namespaceb.net\">Hello|there|Camel</nsb:a>"
                  + "<a xmlns=\"http://defaultNamespace.net\">Hello|there|Camel</a><a>Hello|there|Camel</a></aRoot>";
        Document doc = context.getTypeConverter().convertTo(Document.class, body);
        Field logField = XPathBuilder.class.getDeclaredField("LOG");
        logField.setAccessible(true);

        Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(logField, logField.getModifiers() & ~Modifier.FINAL);

        logField.set(null, l);

        NodeList list = XPathBuilder.xpath("//*", NodeList.class).evaluate(context, doc, NodeList.class);
        assertNotNull(list);

        verify(l, never()).info(argThat(containsString("Namespaces discovered in message")), any(Object.class));
    }

    private static int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            version = StringHelper.before(version, ".", version);
            version = StringHelper.before(version, "-", version);
        }
        return Integer.parseInt(version);
    }
}
