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
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmlunit.assertj3.XmlAssert;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class XMLTokenExpressionIteratorGroupingTest {

    // the input containing multiple Cs
    private static final byte[] TEST_BODY = ("<?xml version='1.0' encoding='UTF-8'?>"
                                             + "<g:A xmlns:g='urn:g'>"
                                             + "<c:B attr='1' xmlns:c='urn:c'>"
                                             + "<c:C attr='1'>peach</c:C>"
                                             + "<c:C attr='2'/>"
                                             + "<c:C attr='3'>orange</c:C>"
                                             + "<c:C attr='4'/>"
                                             + "</c:B>"
                                             + "<c:B attr='2' xmlns:c='urn:c'>"
                                             + "<c:C attr='5'>mango</c:C>"
                                             + "<c:C attr='6'/>"
                                             + "<c:C attr='7'>pear</c:C>"
                                             + "<c:C attr='8'/>"
                                             + "</c:B>"
                                             + "</g:A>")
            .getBytes();

    // one extracted C in its wrapped context per token
    private static final String[] RESULTS_WRAPPED_SIZE1 = {
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                            + "<g:A xmlns:g='urn:g'>"
                                                            + "<c:B attr='1' xmlns:c='urn:c'>"
                                                            + "<c:C attr='1'>peach</c:C>"
                                                            + "</c:B>"
                                                            + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                        + "<g:A xmlns:g='urn:g'>"
                                                                        + "<c:B attr='1' xmlns:c='urn:c'>"
                                                                        + "<c:C attr='2'/>"
                                                                        + "</c:B>"
                                                                        + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                                    + "<g:A xmlns:g='urn:g'>"
                                                                                    + "<c:B attr='1' xmlns:c='urn:c'>"
                                                                                    + "<c:C attr='3'>orange</c:C>"
                                                                                    + "</c:B>"
                                                                                    + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                                                + "<g:A xmlns:g='urn:g'>"
                                                                                                + "<c:B attr='1' xmlns:c='urn:c'>"
                                                                                                + "<c:C attr='4'/>"
                                                                                                + "</c:B>"
                                                                                                + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                                                            + "<g:A xmlns:g='urn:g'>"
                                                                                                            + "<c:B attr='2' xmlns:c='urn:c'>"
                                                                                                            + "<c:C attr='5'>mango</c:C>"
                                                                                                            + "</c:B>"
                                                                                                            + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                                                                        + "<g:A xmlns:g='urn:g'>"
                                                                                                                        + "<c:B attr='2' xmlns:c='urn:c'>"
                                                                                                                        + "<c:C attr='6'/>"
                                                                                                                        + "</c:B>"
                                                                                                                        + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                                                                                    + "<g:A xmlns:g='urn:g'>"
                                                                                                                                    + "<c:B attr='2' xmlns:c='urn:c'>"
                                                                                                                                    + "<c:C attr='7'>pear</c:C>"
                                                                                                                                    + "</c:B>"
                                                                                                                                    + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                                                                                                + "<g:A xmlns:g='urn:g'>"
                                                                                                                                                + "<c:B attr='2' xmlns:c='urn:c'>"
                                                                                                                                                + "<c:C attr='8'/>"
                                                                                                                                                + "</c:B>"
                                                                                                                                                + "</g:A>" };

    // two extracted Cs in their wrapped context per token
    private static final String[] RESULTS_WRAPPED_SIZE2 = {
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                            + "<g:A xmlns:g='urn:g'>"
                                                            + "<c:B attr='1' xmlns:c='urn:c'>"
                                                            + "<c:C attr='1'>peach</c:C>"
                                                            + "<c:C attr='2'/>"
                                                            + "</c:B>"
                                                            + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                        + "<g:A xmlns:g='urn:g'>"
                                                                        + "<c:B attr='1' xmlns:c='urn:c'>"
                                                                        + "<c:C attr='3'>orange</c:C>"
                                                                        + "<c:C attr='4'/>"
                                                                        + "</c:B>"
                                                                        + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                                    + "<g:A xmlns:g='urn:g'>"
                                                                                    + "<c:B attr='2' xmlns:c='urn:c'>"
                                                                                    + "<c:C attr='5'>mango</c:C>"
                                                                                    + "<c:C attr='6'/>"
                                                                                    + "</c:B>"
                                                                                    + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                                                + "<g:A xmlns:g='urn:g'>"
                                                                                                + "<c:B attr='2' xmlns:c='urn:c'>"
                                                                                                + "<c:C attr='7'>pear</c:C>"
                                                                                                + "<c:C attr='8'/>"
                                                                                                + "</c:B>"
                                                                                                + "</g:A>" };

    // at most three extracted Cs in their common wrapped context per token
    private static final String[] RESULTS_WRAPPED_SIZE3L = {
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                             + "<g:A xmlns:g='urn:g'>"
                                                             + "<c:B attr='1' xmlns:c='urn:c'>"
                                                             + "<c:C attr='1'>peach</c:C>"
                                                             + "<c:C attr='2'/>"
                                                             + "<c:C attr='3'>orange</c:C>"
                                                             + "</c:B>"
                                                             + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                         + "<g:A xmlns:g='urn:g'>"
                                                                         + "<c:B attr='1' xmlns:c='urn:c'>"
                                                                         + "<c:C attr='4'/>"
                                                                         + "</c:B>"
                                                                         + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                                     + "<g:A xmlns:g='urn:g'>"
                                                                                     + "<c:B attr='2' xmlns:c='urn:c'>"
                                                                                     + "<c:C attr='5'>mango</c:C>"
                                                                                     + "<c:C attr='6'/>"
                                                                                     + "<c:C attr='7'>pear</c:C>"
                                                                                     + "</c:B>"
                                                                                     + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                                                 + "<g:A xmlns:g='urn:g'>"
                                                                                                 + "<c:B attr='2' xmlns:c='urn:c'>"
                                                                                                 + "<c:C attr='8'/>"
                                                                                                 + "</c:B>"
                                                                                                 + "</g:A>" };

    // three extracted Cs in their corresponding wrapped contexts per token
    private static final String[] RESULTS_WRAPPED_SIZE3U = {
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                             + "<g:A xmlns:g='urn:g'>"
                                                             + "<c:B attr='1' xmlns:c='urn:c'>"
                                                             + "<c:C attr='1'>peach</c:C>"
                                                             + "<c:C attr='2'/>"
                                                             + "<c:C attr='3'>orange</c:C>"
                                                             + "</c:B>"
                                                             + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                         + "<g:A xmlns:g='urn:g'>"
                                                                         + "<c:B attr='1' xmlns:c='urn:c'>"
                                                                         + "<c:C attr='4'/>"
                                                                         + "</c:B>"
                                                                         + "<c:B attr='2' xmlns:c='urn:c'>"
                                                                         + "<c:C attr='5'>mango</c:C>"
                                                                         + "<c:C attr='6'/>"
                                                                         + "</c:B>"
                                                                         + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                                     + "<g:A xmlns:g='urn:g'>"
                                                                                     + "<c:B attr='2' xmlns:c='urn:c'>"
                                                                                     + "<c:C attr='7'>pear</c:C>"
                                                                                     + "<c:C attr='8'/>"
                                                                                     + "</c:B>"
                                                                                     + "</g:A>" };

    // four extracted Cs in their wrapped context per token
    private static final String[] RESULTS_WRAPPED_SIZE4 = {
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                            + "<g:A xmlns:g='urn:g'>"
                                                            + "<c:B attr='1' xmlns:c='urn:c'>"
                                                            + "<c:C attr='1'>peach</c:C>"
                                                            + "<c:C attr='2'/>"
                                                            + "<c:C attr='3'>orange</c:C>"
                                                            + "<c:C attr='4'/>"
                                                            + "</c:B>"
                                                            + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                        + "<g:A xmlns:g='urn:g'>"
                                                                        + "<c:B attr='2' xmlns:c='urn:c'>"
                                                                        + "<c:C attr='5'>mango</c:C>"
                                                                        + "<c:C attr='6'/>"
                                                                        + "<c:C attr='7'>pear</c:C>"
                                                                        + "<c:C attr='8'/>"
                                                                        + "</c:B>"
                                                                        + "</g:A>" };

    // at most five extracted Cs in their common wrapped context per token
    private static final String[] RESULTS_WRAPPED_SIZE5L = RESULTS_WRAPPED_SIZE4;

    // five extracted Cs in their corresponding wrapped contexts per token
    private static final String[] RESULTS_WRAPPED_SIZE5U = {
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                             + "<g:A xmlns:g='urn:g'>"
                                                             + "<c:B attr='1' xmlns:c='urn:c'>"
                                                             + "<c:C attr='1'>peach</c:C>"
                                                             + "<c:C attr='2'/>"
                                                             + "<c:C attr='3'>orange</c:C>"
                                                             + "<c:C attr='4'/>"
                                                             + "</c:B>"
                                                             + "<c:B attr='2' xmlns:c='urn:c'>"
                                                             + "<c:C attr='5'>mango</c:C>"
                                                             + "</c:B>"
                                                             + "</g:A>",
            "<?xml version='1.0' encoding='UTF-8'?>"
                                                                         + "<g:A xmlns:g='urn:g'>"
                                                                         + "<c:B attr='2' xmlns:c='urn:c'>"
                                                                         + "<c:C attr='6'/>"
                                                                         + "<c:C attr='7'>pear</c:C>"
                                                                         + "<c:C attr='8'/>"
                                                                         + "</c:B>"
                                                                         + "</g:A>" };

    private static final String[] RESULTS_INJECTED_SIZE1 = {
            "<c:C attr='1' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">peach</c:C>",
            "<c:C attr='2' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>",
            "<c:C attr='3' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">orange</c:C>",
            "<c:C attr='4' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>",
            "<c:C attr='5' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">mango</c:C>",
            "<c:C attr='6' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>",
            "<c:C attr='7' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">pear</c:C>",
            "<c:C attr='8' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>" };

    private static final String[] RESULTS_INJECTED_SIZE2 = {
            "<group>"
                                                             + "<c:C attr='1' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">peach</c:C>"
                                                             + "<c:C attr='2' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
                                                             + "</group>",
            "<group>"
                                                                           + "<c:C attr='3' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">orange</c:C>"
                                                                           + "<c:C attr='4' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
                                                                           + "</group>",
            "<group>"
                                                                                         + "<c:C attr='5' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">mango</c:C>"
                                                                                         + "<c:C attr='6' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
                                                                                         + "</group>",
            "<group>"
                                                                                                       + "<c:C attr='7' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">pear</c:C>"
                                                                                                       + "<c:C attr='8' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
                                                                                                       + "</group>" };

    private static final String[] RESULTS_INJECTED_SIZE3 = {
            "<group>"
                                                             + "<c:C attr='1' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">peach</c:C>"
                                                             + "<c:C attr='2' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
                                                             + "<c:C attr='3' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">orange</c:C>"
                                                             + "</group>",
            "<group>"
                                                                           + "<c:C attr='4' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
                                                                           + "<c:C attr='5' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">mango</c:C>"
                                                                           + "<c:C attr='6' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
                                                                           + "</group>",
            "<group>"
                                                                                         + "<c:C attr='7' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">pear</c:C>"
                                                                                         + "<c:C attr='8' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
                                                                                         + "</group>" };

    private static final String[] RESULTS_INJECTED_SIZE4 = {
            "<group>"
                                                             + "<c:C attr='1' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">peach</c:C>"
                                                             + "<c:C attr='2' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
                                                             + "<c:C attr='3' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">orange</c:C>"
                                                             + "<c:C attr='4' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
                                                             + "</group>",
            "<group>"
                                                                           + "<c:C attr='5' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">mango</c:C>"
                                                                           + "<c:C attr='6' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
                                                                           + "<c:C attr='7' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">pear</c:C>"
                                                                           + "<c:C attr='8' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
                                                                           + "</group>" };

    private static final String[] RESULTS_INJECTED_SIZE5 = {
            "<group>"
                                                             + "<c:C attr='1' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">peach</c:C>"
                                                             + "<c:C attr='2' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
                                                             + "<c:C attr='3' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">orange</c:C>"
                                                             + "<c:C attr='4' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
                                                             + "<c:C attr='5' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">mango</c:C>"
                                                             + "</group>",
            "<group>"
                                                                           + "<c:C attr='6' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
                                                                           + "<c:C attr='7' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\">pear</c:C>"
                                                                           + "<c:C attr='8' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
                                                                           + "</group>" };

    private Map<String, String> nsmap;

    @BeforeEach
    public void setUp() {
        nsmap = new HashMap<>();
        nsmap.put("g", "urn:g");
        nsmap.put("c", "urn:c");
    }

    // wrapped mode
    @Test
    public void testExtractWrappedSize1() throws Exception {
        invokeAndVerify("//c:C",
                'w', 1, new ByteArrayInputStream(TEST_BODY), "utf-8", RESULTS_WRAPPED_SIZE1);
    }

    @Test
    public void testExtractWrappedSize2() throws Exception {
        invokeAndVerify("//c:C",
                'w', 2, new ByteArrayInputStream(TEST_BODY), "utf-8", RESULTS_WRAPPED_SIZE2);
    }

    @Test
    public void testExtractWrappedSize3L() throws Exception {
        invokeAndVerify("//c:C",
                'w', 3, new ByteArrayInputStream(TEST_BODY), "utf-8", RESULTS_WRAPPED_SIZE3L);
    }

    // disabled: not working for now as the context extraction across two ancestor paths is not working
    public void disabledtestExtractWrappedSize3U() throws Exception {
        invokeAndVerify("//c:C",
                'W', 3, new ByteArrayInputStream(TEST_BODY), "utf-8", RESULTS_WRAPPED_SIZE3U);
    }

    @Test
    public void testExtractWrappedSize4() throws Exception {
        invokeAndVerify("//c:C",
                'w', 4, new ByteArrayInputStream(TEST_BODY), "utf-8", RESULTS_WRAPPED_SIZE4);
    }

    @Test
    public void testExtractWrappedSize5L() throws Exception {
        invokeAndVerify("//c:C",
                'w', 5, new ByteArrayInputStream(TEST_BODY), "utf-8", RESULTS_WRAPPED_SIZE5L);
    }

    // disabled: not working for now as the context extraction across two ancestor paths is not working
    public void disabledtestExtractWrappedSize5U() throws Exception {
        invokeAndVerify("//c:C",
                'W', 5, new ByteArrayInputStream(TEST_BODY), "utf-8", RESULTS_WRAPPED_SIZE5U);
    }

    // injected mode
    @Test
    public void testExtractInjectedSize1() throws Exception {
        invokeAndVerify("//c:C",
                'i', 1, new ByteArrayInputStream(TEST_BODY), "utf-8", RESULTS_INJECTED_SIZE1);
    }

    @Test
    public void testExtractInjectedSize2() throws Exception {
        invokeAndVerify("//c:C",
                'i', 2, new ByteArrayInputStream(TEST_BODY), "utf-8", RESULTS_INJECTED_SIZE2);
    }

    @Test
    public void testExtractInjectedSize3() throws Exception {
        invokeAndVerify("//c:C",
                'i', 3, new ByteArrayInputStream(TEST_BODY), "utf-8", RESULTS_INJECTED_SIZE3);
    }

    @Test
    public void testExtractInjectedSize4() throws Exception {
        invokeAndVerify("//c:C",
                'i', 4, new ByteArrayInputStream(TEST_BODY), "utf-8", RESULTS_INJECTED_SIZE4);
    }

    @Test
    public void testExtractInjectedSize5() throws Exception {
        invokeAndVerify("//c:C",
                'i', 5, new ByteArrayInputStream(TEST_BODY), "utf-8", RESULTS_INJECTED_SIZE5);
    }

    @Test
    public void testExtractWrappedLeftOver() throws Exception {
        final byte[] data = ("<?xml version='1.0' encoding='UTF-8'?><g:A xmlns:g='urn:g'><c:B attr='1' xmlns:c='urn:c'>"
                             + "<c:C attr='1'>peach</c:C>"
                             + "<c:C attr='2'/>"
                             + "<c:C attr='3'>orange</c:C>"
                             + "</c:B></g:A>")
                .getBytes();
        final String[] results = {
                "<?xml version='1.0' encoding='UTF-8'?><g:A xmlns:g='urn:g'><c:B attr='1' xmlns:c='urn:c'>"
                                   + "<c:C attr='1'>peach</c:C><c:C attr='2'/>"
                                   + "</c:B></g:A>",
                "<?xml version='1.0' encoding='UTF-8'?><g:A xmlns:g='urn:g'><c:B attr='1' xmlns:c='urn:c'>"
                                                     + "<c:C attr='3'>orange</c:C>"
                                                     + "</c:B></g:A>" };
        invokeAndVerify("//c:C",
                'w', 2, new ByteArrayInputStream(data), "utf-8", results);
    }

    private void invokeAndVerify(
            String path, char mode, int group,
            InputStream in, String charset, String[] expected)
            throws Exception {
        XMLTokenExpressionIterator xtei = new XMLTokenExpressionIterator(path, mode);
        xtei.setNamespaces(nsmap);
        xtei.setGroup(group);

        Iterator<?> it = xtei.createIterator(new InputStreamReader(in, "utf-8"));
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
