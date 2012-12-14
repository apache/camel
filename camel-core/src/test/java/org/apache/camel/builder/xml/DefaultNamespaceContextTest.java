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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.camel.ContextTestSupport;

/**
 * @version 
 */
public class DefaultNamespaceContextTest extends ContextTestSupport {

    public void testDefaultNamespaceContextEmpty() throws Exception {
        XPathBuilder builder = XPathBuilder.xpath("/foo");
        builder.start();
        DefaultNamespaceContext context = builder.getNamespaceContext();
        assertNotNull(context);

        String uri = context.getNamespaceURI("foo");
        assertNull(uri);

        String prefix = context.getPrefix("foo");
        assertNull(prefix);

        Iterator<String> it = context.getPrefixes("foo");
        assertEquals(false, it.hasNext());
    }

    public void testDefaultNamespaceContextPre() throws Exception {
        XPathBuilder builder = XPathBuilder.xpath("/foo").namespace("pre", "http://acme/cheese");
        builder.start();
        DefaultNamespaceContext context = builder.getNamespaceContext();
        assertNotNull(context);

        String uri = context.getNamespaceURI("pre");
        assertEquals("http://acme/cheese", uri);

        String prefix = context.getPrefix("http://acme/cheese");
        assertEquals("pre", prefix);

        Iterator<String> it = context.getPrefixes("http://acme/cheese");
        assertEquals(true, it.hasNext());
        assertEquals("pre", it.next());
    }

    public void testDefaultNamespaceContextDualNamespaces() throws Exception {
        XPathBuilder builder = XPathBuilder.xpath("/foo").namespace("pre", "http://acme/cheese").namespace("bar", "http://acme/bar");
        builder.start();
        DefaultNamespaceContext context = builder.getNamespaceContext();
        assertNotNull(context);

        String uri = context.getNamespaceURI("pre");
        assertEquals("http://acme/cheese", uri);
        String uri2 = context.getNamespaceURI("bar");
        assertEquals("http://acme/bar", uri2);

        String prefix = context.getPrefix("http://acme/cheese");
        assertEquals("pre", prefix);
        String prefix2 = context.getPrefix("http://acme/bar");
        assertEquals("bar", prefix2);

        Iterator<String> it = context.getPrefixes("http://acme/cheese");
        assertEquals(true, it.hasNext());
        assertEquals("pre", it.next());

        Iterator<String> it2 = context.getPrefixes("http://acme/bar");
        assertEquals(true, it2.hasNext());
        assertEquals("bar", it2.next());
    }

    public void testDefaultNamespaceContextParent() throws Exception {
        XPathBuilder builder = XPathBuilder.xpath("/foo");
        builder.start();
        DefaultNamespaceContext context = builder.getNamespaceContext();
        assertNotNull(context);

        String uri = context.getNamespaceURI("in");
        assertEquals(Namespaces.IN_NAMESPACE, uri);
        String prefix = context.getPrefix(Namespaces.IN_NAMESPACE);
        assertEquals("in", prefix);
        Iterator<String> it = context.getPrefixes(Namespaces.IN_NAMESPACE);
        assertEquals(true, it.hasNext());
        assertEquals("in", it.next());

        String uri2 = context.getNamespaceURI("out");
        assertEquals(Namespaces.OUT_NAMESPACE, uri2);

        String uri3 = context.getNamespaceURI("env");
        assertEquals(Namespaces.ENVIRONMENT_VARIABLES, uri3);

        String uri4 = context.getNamespaceURI("system");
        assertEquals(Namespaces.SYSTEM_PROPERTIES_NAMESPACE, uri4);
    }

    public void testDefaultNamespaceContextCtr() throws Exception {
        DefaultNamespaceContext context = new DefaultNamespaceContext();
        
        // should not have any namespaces
        String uri = context.getNamespaceURI("in");
        assertEquals(null, uri);
    }

    public void testDefaultNamespaceContextAnotherCtr() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("foo", "http://acme/cheese");
        DefaultNamespaceContext context = new DefaultNamespaceContext(null, map);

        // should not have any default namespaces
        String uri = context.getNamespaceURI("in");
        assertEquals(null, uri);

        String uri2 = context.getNamespaceURI("foo");
        assertEquals("http://acme/cheese", uri2);
    }
}
