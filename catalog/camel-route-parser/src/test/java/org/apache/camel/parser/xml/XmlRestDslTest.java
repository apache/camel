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
package org.apache.camel.parser.xml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import org.apache.camel.parser.XmlRestDslParser;
import org.apache.camel.parser.model.RestConfigurationDetails;
import org.apache.camel.parser.model.RestServiceDetails;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class XmlRestDslTest {

    @Test
    public void testXmlTree() throws Exception {
        InputStream is = new FileInputStream("src/test/resources/org/apache/camel/parser/xml/myrest.xml");
        String fqn = "src/test/resources/org/apache/camel/camel/parser/xml/myrest.xml";
        String baseDir = "src/test/resources";
        List<RestConfigurationDetails> list = XmlRestDslParser.parseRestConfiguration(is, baseDir, fqn);

        assertEquals(1, list.size());
        RestConfigurationDetails details = list.get(0);
        assertEquals("src/test/resources/org/apache/camel/camel/parser/xml/myrest.xml", details.getFileName());
        assertNull(details.getMethodName());
        assertNull(details.getClassName());

        assertEquals("29", details.getLineNumber());
        assertEquals("35", details.getLineNumberEnd());
        assertEquals("1234", details.getPort());
        assertEquals("myapi", details.getContextPath());
        assertEquals("jetty", details.getComponent());
        assertEquals("json", details.getBindingMode());
        assertEquals("swagger", details.getApiComponent());
        assertEquals("myapi/swagger", details.getApiContextPath());
        assertEquals("localhost", details.getApiHost());
        assertEquals("true", details.getSkipBindingOnErrorCode());
        assertEquals("https", details.getScheme());
        assertEquals("allLocalIp", details.getHostNameResolver());

        assertEquals(1, details.getComponentProperties().size());
        assertEquals("123", details.getComponentProperties().get("foo"));
        assertEquals(1, details.getEndpointProperties().size());
        assertEquals("false", details.getEndpointProperties().get("pretty"));
        assertEquals(1, details.getEndpointProperties().size());
        assertEquals("456", details.getConsumerProperties().get("bar"));
        assertEquals(2, details.getCorsHeaders().size());
        assertEquals("value1", details.getCorsHeaders().get("key1"));
        assertEquals("value2", details.getCorsHeaders().get("key2"));
    }

    @Test
    public void parseRestService() throws Exception {
        InputStream is = new FileInputStream("src/test/resources/org/apache/camel/parser/xml/myrest.xml");
        String fqn = "src/test/resources/org/apache/camel/camel/parser/xml/myrest.xml";
        String baseDir = "src/test/resources";
        List<RestServiceDetails> list = XmlRestDslParser.parseRestService(is, baseDir, fqn);

        assertEquals(1, list.size());
        RestServiceDetails details = list.get(0);
        assertEquals("src/test/resources/org/apache/camel/camel/parser/xml/myrest.xml", details.getFileName());
        assertNull(details.getMethodName());
        assertNull(details.getClassName());

        assertEquals("37", details.getLineNumber());
        assertEquals("47", details.getLineNumberEnd());
        assertEquals("src/test/resources/org/apache/camel/camel/parser/xml/myrest.xml", details.getFileName());
        assertNull(details.getMethodName());
        assertNull(details.getClassName());

        assertEquals("/foo", details.getPath());
        assertEquals("my foo service", details.getDescription());
        assertEquals("json", details.getProduces());
        assertEquals("json", details.getProduces());
        assertEquals(2, details.getVerbs().size());
        assertEquals("get", details.getVerbs().get(0).getMethod());
        assertEquals("{id}", details.getVerbs().get(0).getUri());
        assertEquals("get by id", details.getVerbs().get(0).getDescription());
        assertEquals("log:id", details.getVerbs().get(0).getTo());
        assertEquals("false", details.getVerbs().get(0).getApiDocs());
        assertEquals("post", details.getVerbs().get(1).getMethod());
        assertEquals("post something", details.getVerbs().get(1).getDescription());
        assertEquals("xml", details.getVerbs().get(1).getBindingMode());
        assertEquals("log:post", details.getVerbs().get(1).getToD());
        assertNull(details.getVerbs().get(1).getUri());
    }


}
