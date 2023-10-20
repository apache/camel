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
package org.apache.camel.language.xtokenizer;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xmlunit.assertj3.XmlAssert;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class XMLTokenizeExpressionIteratorWModeTest {

    private static final String XML_BEFORE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                             + "<root>\n"
                                             + "    <Level1>\n"
                                             + "        <Level2preceding>Included</Level2preceding>\n"
                                             + "        <Level2following>Not Included</Level2following>\n"
                                             + "        <Level2>\n"
                                             + "            <data>Hello, World!</data>\n"
                                             + "            <data>Hello, Camel!</data>\n"
                                             + "            <data>Hello, Apache Foundation!</data>\n"
                                             + "        </Level2>\n"
                                             + "    </Level1>\n"
                                             + "</root>";

    private static final String XML_AFTER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                            + "<root>\n"
                                            + "    <Level1>\n"
                                            + "        <Level2preceding>Included</Level2preceding>\n"
                                            + "        <Level2>\n"
                                            + "            <data>Hello, World!</data>\n"
                                            + "            <data>Hello, Camel!</data>\n"
                                            + "            <data>Hello, Apache Foundation!</data>\n"
                                            + "        </Level2>\n"
                                            + "        <Level2following>Not Included</Level2following>\n"
                                            + "    </Level1>\n"
                                            + "</root>";

    private static final String RS1_BEFORE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                             + "<root>\n"
                                             + "    <Level1>\n"
                                             + "        <Level2preceding>Included</Level2preceding>\n"
                                             + "        <Level2following>Not Included</Level2following>\n"
                                             + "        <Level2>\n"
                                             + "            <data>Hello, World!</data></Level2></Level1></root>";

    private static final String RS2_BEFORE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                             + "<root>\n"
                                             + "    <Level1>\n"
                                             + "        <Level2preceding>Included</Level2preceding>\n"
                                             + "        <Level2following>Not Included</Level2following>\n"
                                             + "        <Level2>\n"
                                             + "            <data>Hello, Camel!</data></Level2></Level1></root>";

    private static final String RS3_BEFORE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                             + "<root>\n"
                                             + "    <Level1>\n"
                                             + "        <Level2preceding>Included</Level2preceding>\n"
                                             + "        <Level2following>Not Included</Level2following>\n"
                                             + "        <Level2>\n"
                                             + "            <data>Hello, Apache Foundation!</data></Level2></Level1></root>";

    private static final String[] RS_BEFORE = { RS1_BEFORE, RS2_BEFORE, RS3_BEFORE };

    private static final String RS1_AFTER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                            + "<root>\n"
                                            + "    <Level1>\n"
                                            + "        <Level2preceding>Included</Level2preceding>\n"
                                            + "        <Level2>\n"
                                            + "            <data>Hello, World!</data><Level2following>Not Included</Level2following></Level2></Level1></root>";

    private static final String RS2_AFTER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                            + "<root>\n"
                                            + "    <Level1>\n"
                                            + "        <Level2preceding>Included</Level2preceding>\n"
                                            + "        <Level2>\n"
                                            + "            <data>Hello, Camel!</data><Level2following>Not Included</Level2following></Level2></Level1></root>";

    private static final String RS3_AFTER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                                            + "<root>\n"
                                            + "    <Level1>\n"
                                            + "        <Level2preceding>Included</Level2preceding>\n"
                                            + "        <Level2>\n"
                                            + "            <data>Hello, Apache Foundation!</data><Level2following>Not Included</Level2following></Level2></Level1></root>";

    private static final String[] RS_AFTER = { RS1_AFTER, RS2_AFTER, RS3_AFTER };

    private Map<String, String> nsmap;

    @BeforeEach
    public void setUp() {
        nsmap = new HashMap<>();
        nsmap.put("ns1", "");
    }

    @Test
    public void testWModeBefore() throws Exception {
        InputStream is = new ByteArrayInputStream(XML_BEFORE.getBytes(StandardCharsets.UTF_8));
        invokeAndVerify("/root/Level1/Level2/data", 'w', is, RS_BEFORE);
    }

    @Test
    @Disabled("TODO: https://issues.apache.org/jira/browse/CAMEL-17466")
    public void testWModeAfter() throws Exception {
        InputStream is = new ByteArrayInputStream(XML_AFTER.getBytes(StandardCharsets.UTF_8));
        invokeAndVerify("/root/Level1/Level2/data", 'w', is, RS_AFTER);
    }

    private void invokeAndVerify(String path, char mode, InputStream in, String[] expected) throws Exception {
        XMLTokenExpressionIterator xtei = new XMLTokenExpressionIterator(path, mode);
        xtei.setNamespaces(nsmap);

        Iterator<?> it = xtei.createIterator(in);
        List<String> results = new ArrayList<>();
        while (it.hasNext()) {
            results.add((String) it.next());
        }
        ((Closeable) it).close();

        assertEquals(expected.length, results.size(), "token count");
        for (int i = 0; i < expected.length; i++) {
            String expectedToken = expected[i];
            if (expectedToken.startsWith("<")) {
                XmlAssert.assertThat(results.get(i)).and(expectedToken).areIdentical();
            } else {
                assertEquals(expectedToken, results.get(i), "mismatch [" + i + "]");
            }
        }
    }

}
