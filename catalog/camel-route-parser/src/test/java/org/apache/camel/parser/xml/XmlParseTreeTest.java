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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

import org.apache.camel.parser.XmlRouteParser;
import org.apache.camel.parser.model.CamelNodeDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XmlParseTreeTest {

    private static final Logger LOG = LoggerFactory.getLogger(XmlParseTreeTest.class);

    @TempDir
    File tempDir;

    @Test
    void testXmlTree() throws Exception {
        InputStream is = new FileInputStream("src/test/resources/org/apache/camel/parser/xml/mycamel.xml");
        String fqn = "src/test/resources/org/apache/camel/camel/parser/xml/mycamel.xml";
        String baseDir = "src/test/resources";
        List<CamelNodeDetails> list = XmlRouteParser.parseXmlRouteTree(is, baseDir, fqn);

        assertEquals(1, list.size());
        CamelNodeDetails details = list.get(0);
        assertEquals("src/test/resources/org/apache/camel/camel/parser/xml/mycamel.xml", details.getFileName());
        assertEquals("myRoute", details.getRouteId());
        assertNull(details.getMethodName());
        assertNull(details.getClassName());

        String tree = details.dump(0);
        LOG.info("\n{}", tree);

        assertTrue(tree.contains("32\tfrom"));
        assertTrue(tree.contains("35\t  transform"));
        assertTrue(tree.contains("39\t  to"));
    }

    @Test
    void testXmlTreeWithEmptyRoute() throws Exception {
        String textTotest = "<camelContext id=\"camel\" xmlns=\"http://camel.apache.org/schema/spring\">\r\n" +
                            "    <route id=\"a route\">\r\n" +
                            "    </route>\r\n" +
                            "</camelContext>\n";
        File camelFile = new File(tempDir, "testXmlTreeWithEmptyRoute.xml");
        Files.copy(new ByteArrayInputStream(textTotest.getBytes()), camelFile.toPath());
        List<CamelNodeDetails> list = XmlRouteParser.parseXmlRouteTree(new ByteArrayInputStream(textTotest.getBytes()), "",
                camelFile.getAbsolutePath());

        assertEquals(0, list.size());
    }

}
