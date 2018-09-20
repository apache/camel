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
package org.apache.camel.parser.xml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import org.apache.camel.parser.XmlRestDslParser;
import org.apache.camel.parser.model.RestConfigurationDetails;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

public class XmlRestDslTest {

    private static final Logger LOG = LoggerFactory.getLogger(XmlParseTreeTest.class);

    @Test
    public void testXmlTree() throws Exception {
        InputStream is = new FileInputStream("src/test/resources/org/apache/camel/parser/xml/myrest.xml");
        String fqn = "src/test/resources/org/apache/camel/camel/parser/xml/myrest.xml";
        String baseDir = "src/test/resources";
        List<RestConfigurationDetails> list = XmlRestDslParser.parseRestConfiguration(is, baseDir, fqn);

        assertEquals(1, list.size());
        RestConfigurationDetails details = list.get(0);
        assertEquals("src/test/resources/org/apache/camel/camel/parser/xml/myrest.xml", details.getFileName());
        assertEquals(null, details.getMethodName());
        assertEquals(null, details.getClassName());

        assertEquals("29", details.getLineNumber());
        assertEquals("30", details.getLineNumberEnd());
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

    }

}
