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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmlunit.assertj3.XmlAssert;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
public class XMLTokenExpressionIteratorTest {
    private static final byte[] TEST_BODY = ("<?xml version='1.0' encoding='UTF-8'?>"
                                             + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt>"
                                             + "<c:parent some_attr='1' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                             + "<c:child some_attr='a' anotherAttr='a'></c:child>"
                                             + "<c:child some_attr='b' anotherAttr='b'/>"
                                             + "</c:parent>"
                                             + "<c:parent some_attr='2' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                             + "<c:child some_attr='c' anotherAttr='c'></c:child>"
                                             + "<c:child some_attr='d' anotherAttr='d'/>"
                                             + "</c:parent>"
                                             + "</grandparent>"
                                             + "<grandparent><uncle>ben</uncle><aunt/>"
                                             + "<c:parent some_attr='3' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                             + "<c:child some_attr='e' anotherAttr='e'></c:child>"
                                             + "<c:child some_attr='f' anotherAttr='f'/>"
                                             + "</c:parent>"
                                             + "</grandparent>"
                                             + "</g:greatgrandparent>")
            .getBytes();

    // mixing a default namespace with an explicit namespace for child
    private static final byte[] TEST_BODY_NS_MIXED = ("<?xml version='1.0' encoding='UTF-8'?>"
                                                      + "<g:greatgrandparent xmlns:g='urn:g'><grandparent>"
                                                      + "<parent some_attr='1' xmlns:c='urn:c' xmlns=\"urn:c\">"
                                                      + "<child some_attr='a' anotherAttr='a'></child>"
                                                      + "<x:child xmlns:x='urn:c' some_attr='b' anotherAttr='b'/>"
                                                      + "</parent>"
                                                      + "<c:parent some_attr='2' xmlns:c='urn:c'>"
                                                      + "<child some_attr='c' anotherAttr='c' xmlns='urn:c'></child>"
                                                      + "<c:child some_attr='d' anotherAttr='d'/>"
                                                      + "</c:parent>"
                                                      + "</grandparent>"
                                                      + "</g:greatgrandparent>")
            .getBytes();

    // mixing a no namespace with an explicit namespace for child
    private static final byte[] TEST_BODY_NO_NS_MIXED = ("<?xml version='1.0' encoding='UTF-8'?>"
                                                         + "<g:greatgrandparent xmlns:g='urn:g'><grandparent>"
                                                         + "<parent some_attr='1' xmlns:c='urn:c' xmlns=\"urn:c\">"
                                                         + "<child some_attr='a' anotherAttr='a' xmlns=''></child>"
                                                         + "<x:child xmlns:x='urn:c' some_attr='b' anotherAttr='b'/>"
                                                         + "</parent>"
                                                         + "<c:parent some_attr='2' xmlns:c='urn:c'>"
                                                         + "<child some_attr='c' anotherAttr='c'></child>"
                                                         + "<c:child some_attr='d' anotherAttr='d'/>"
                                                         + "</c:parent>"
                                                         + "</grandparent>"
                                                         + "</g:greatgrandparent>")
            .getBytes();

    // mixing different namespaces within a tag
    private static final byte[] TEST_BODY_MIXED_CHILDREN = ("<?xml version='1.0' encoding='UTF-8'?>"
                                                            + "<greatgrandparent xmlns='urn:g' xmlns:c='urn:c' xmlns:x='urn:x'>"
                                                            + "<grandparent>"
                                                            + "<x:uncle>bob</x:uncle>"
                                                            + "<x:aunt>emma</x:aunt>"
                                                            + "</grandparent>"
                                                            + "<grandparent>"
                                                            + "<c:parent some_attr='1'>"
                                                            + "<c:child some_attr='a' anotherAttr='a'></c:child>"
                                                            + "<c:child some_attr='b' anotherAttr='b' />"
                                                            + "</c:parent>"
                                                            + "<c:parent some_attr='2'>"
                                                            + "<c:child some_attr='c' anotherAttr='c'></c:child>"
                                                            + "<c:child some_attr='d' anotherAttr='d' />"
                                                            + "</c:parent>"
                                                            + "</grandparent>"
                                                            + "<grandparent>"
                                                            + "<x:uncle>ben</x:uncle>"
                                                            + "<x:aunt>jenna</x:aunt>"
                                                            + "<c:parent some_attr='3'>"
                                                            + "<c:child some_attr='e' anotherAttr='e'></c:child>"
                                                            + "<c:child some_attr='f' anotherAttr='f' />"
                                                            + "</c:parent>"
                                                            + "</grandparent>"
                                                            + "</greatgrandparent>")
            .getBytes();

    private static final String RESULTS_CW1 = "<?xml version='1.0' encoding='UTF-8'?>"
                                              + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt>"
                                              + "<c:parent some_attr='1' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                              + "<c:child some_attr='a' anotherAttr='a'></c:child>"
                                              + "</c:parent></grandparent></g:greatgrandparent>";
    private static final String RESULTS_CW2 = "<?xml version='1.0' encoding='UTF-8'?>"
                                              + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt>"
                                              + "<c:parent some_attr='1' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                              + "<c:child some_attr='b' anotherAttr='b'/>"
                                              + "</c:parent></grandparent></g:greatgrandparent>";
    private static final String RESULTS_CW3 = "<?xml version='1.0' encoding='UTF-8'?>"
                                              + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt>"
                                              + "<c:parent some_attr='2' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                              + "<c:child some_attr='c' anotherAttr='c'></c:child>"
                                              + "</c:parent></grandparent></g:greatgrandparent>";
    private static final String RESULTS_CW4 = "<?xml version='1.0' encoding='UTF-8'?>"
                                              + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt>"
                                              + "<c:parent some_attr='2' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                              + "<c:child some_attr='d' anotherAttr='d'/>"
                                              + "</c:parent></grandparent></g:greatgrandparent>";
    private static final String RESULTS_CW5 = "<?xml version='1.0' encoding='UTF-8'?>"
                                              + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle>ben</uncle><aunt/>"
                                              + "<c:parent some_attr='3' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                              + "<c:child some_attr='e' anotherAttr='e'></c:child>"
                                              + "</c:parent></grandparent></g:greatgrandparent>";
    private static final String RESULTS_CW6 = "<?xml version='1.0' encoding='UTF-8'?>"
                                              + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle>ben</uncle><aunt/>"
                                              + "<c:parent some_attr='3' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                              + "<c:child some_attr='f' anotherAttr='f'/>"
                                              + "</c:parent></grandparent></g:greatgrandparent>";
    private static final String[] RESULTS_CHILD_WRAPPED = {
            RESULTS_CW1,
            RESULTS_CW2,
            RESULTS_CW3,
            RESULTS_CW4,
            RESULTS_CW5,
            RESULTS_CW6
    };

    private static final String[] RESULTS_CHILD_MIXED = {
            "<child some_attr='a' anotherAttr='a' xmlns=\"urn:c\" xmlns:c=\"urn:c\" xmlns:g=\"urn:g\"></child>",
            "<x:child xmlns:x='urn:c' some_attr='b' anotherAttr='b' xmlns='urn:c' xmlns:c='urn:c' xmlns:g='urn:g'/>",
            "<child some_attr='c' anotherAttr='c' xmlns='urn:c' xmlns:g='urn:g' xmlns:c='urn:c'></child>",
            "<c:child some_attr='d' anotherAttr='d' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
    };

    private static final String RESULTS_CMW1
            = "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent>"
              + "<parent some_attr='1' xmlns:c='urn:c' xmlns=\"urn:c\">"
              + "<child some_attr='a' anotherAttr='a'></child></parent></grandparent></g:greatgrandparent>";
    private static final String RESULTS_CMW2
            = "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent>"
              + "<parent some_attr='1' xmlns:c='urn:c' xmlns=\"urn:c\">"
              + "<x:child xmlns:x='urn:c' some_attr='b' anotherAttr='b'/></parent></grandparent></g:greatgrandparent>";
    private static final String RESULTS_CMW3
            = "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent>"
              + "<c:parent some_attr='2' xmlns:c='urn:c'>"
              + "<child some_attr='c' anotherAttr='c' xmlns='urn:c'></child></c:parent></grandparent></g:greatgrandparent>";
    private static final String RESULTS_CMW4
            = "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent>"
              + "<c:parent some_attr='2' xmlns:c='urn:c'>"
              + "<c:child some_attr='d' anotherAttr='d'/></c:parent></grandparent></g:greatgrandparent>";
    private static final String[] RESULTS_CHILD_MIXED_WRAPPED = {
            RESULTS_CMW1,
            RESULTS_CMW2,
            RESULTS_CMW3,
            RESULTS_CMW4
    };

    private static final String[] RESULTS_CHILD = {
            "<c:child some_attr='a' anotherAttr='a' xmlns:c=\"urn:c\" xmlns:d=\"urn:d\" xmlns:g=\"urn:g\"></c:child>",
            "<c:child some_attr='b' anotherAttr='b' xmlns:c=\"urn:c\" xmlns:d=\"urn:d\" xmlns:g=\"urn:g\"/>",
            "<c:child some_attr='c' anotherAttr='c' xmlns:c=\"urn:c\" xmlns:d=\"urn:d\" xmlns:g=\"urn:g\"></c:child>",
            "<c:child some_attr='d' anotherAttr='d' xmlns:c=\"urn:c\" xmlns:d=\"urn:d\" xmlns:g=\"urn:g\"/>",
            "<c:child some_attr='e' anotherAttr='e' xmlns:c=\"urn:c\" xmlns:d=\"urn:d\" xmlns:g=\"urn:g\"></c:child>",
            "<c:child some_attr='f' anotherAttr='f' xmlns:c=\"urn:c\" xmlns:d=\"urn:d\" xmlns:g=\"urn:g\"/>"
    };

    private static final String[] RESULTS_CHILD_NO_NS_MIXED = {
            "<child some_attr='a' anotherAttr='a' xmlns='' xmlns:c='urn:c' xmlns:g='urn:g'></child>",
            "<child some_attr='c' anotherAttr='c' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"></child>",
    };

    private static final String RESULT_CNNMW1
            = "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent>"
              + "<parent some_attr='1' xmlns:c='urn:c' xmlns=\"urn:c\">"
              + "<child some_attr='a' anotherAttr='a' xmlns=''></child></parent></grandparent></g:greatgrandparent>";
    private static final String RESULT_CNNMW2
            = "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent>"
              + "<c:parent some_attr='2' xmlns:c='urn:c'>"
              + "<child some_attr='c' anotherAttr='c'></child></c:parent></grandparent></g:greatgrandparent>";
    // note that there is no preceding sibling to the extracted child
    private static final String[] RESULTS_CHILD_NO_NS_MIXED_WRAPPED = {
            RESULT_CNNMW1,
            RESULT_CNNMW2,
    };

    private static final String[] RESULTS_CHILD_NS_MIXED = {
            "<x:child xmlns:x='urn:c' some_attr='b' anotherAttr='b' xmlns='urn:c' xmlns:c='urn:c' xmlns:g='urn:g'/>",
            "<c:child some_attr='d' anotherAttr='d' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
    };

    private static final String RESULTS_CNMW1
            = "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent>"
              + "<parent some_attr='1' xmlns:c='urn:c' xmlns=\"urn:c\">"
              + "<child some_attr='a' anotherAttr='a' xmlns=''></child>"
              + "<x:child xmlns:x='urn:c' some_attr='b' anotherAttr='b'/></parent></grandparent></g:greatgrandparent>";
    private static final String RESULTS_CNMW2
            = "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent>"
              + "<c:parent some_attr='2' xmlns:c='urn:c'>"
              + "<child some_attr='c' anotherAttr='c'></child>"
              + "<c:child some_attr='d' anotherAttr='d'/></c:parent></grandparent></g:greatgrandparent>";
    // note that there is a preceding sibling to the extracted child
    private static final String[] RESULTS_CHILD_NS_MIXED_WRAPPED = {
            RESULTS_CNMW1,
            RESULTS_CNMW2
    };

    private static final String RESULTS_PW1 = "<?xml version='1.0' encoding='UTF-8'?>"
                                              + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt>"
                                              + "<c:parent some_attr='1' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                              + "<c:child some_attr='a' anotherAttr='a'></c:child>"
                                              + "<c:child some_attr='b' anotherAttr='b'/>"
                                              + "</c:parent></grandparent></g:greatgrandparent>";
    private static final String RESULTS_PW2 = "<?xml version='1.0' encoding='UTF-8'?>"
                                              + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt>"
                                              + "<c:parent some_attr='2' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                              + "<c:child some_attr='c' anotherAttr='c'></c:child>"
                                              + "<c:child some_attr='d' anotherAttr='d'/>"
                                              + "</c:parent></grandparent></g:greatgrandparent>";
    private static final String RESULTS_PW3 = "<?xml version='1.0' encoding='UTF-8'?>"
                                              + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle>ben</uncle><aunt/>"
                                              + "<c:parent some_attr='3' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
                                              + "<c:child some_attr='e' anotherAttr='e'></c:child>"
                                              + "<c:child some_attr='f' anotherAttr='f'/>"
                                              + "</c:parent></grandparent></g:greatgrandparent>";
    private static final String[] RESULTS_PARENT_WRAPPED = {
            RESULTS_PW1,
            RESULTS_PW2,
            RESULTS_PW3,
    };

    private static final String RESULTS_P1 = "<c:parent some_attr='1' xmlns:c='urn:c' xmlns:d=\"urn:d\" xmlns:g='urn:g'>"
                                             + "<c:child some_attr='a' anotherAttr='a'></c:child>"
                                             + "<c:child some_attr='b' anotherAttr='b'/>"
                                             + "</c:parent>";
    private static final String RESULTS_P2 = "<c:parent some_attr='2' xmlns:c='urn:c' xmlns:d=\"urn:d\" xmlns:g='urn:g'>"
                                             + "<c:child some_attr='c' anotherAttr='c'></c:child>"
                                             + "<c:child some_attr='d' anotherAttr='d'/>"
                                             + "</c:parent>";
    private static final String RESULTS_P3 = "<c:parent some_attr='3' xmlns:c='urn:c' xmlns:d=\"urn:d\" xmlns:g='urn:g'>"
                                             + "<c:child some_attr='e' anotherAttr='e'></c:child>"
                                             + "<c:child some_attr='f' anotherAttr='f'/>"
                                             + "</c:parent>";
    private static final String[] RESULTS_PARENT = {
            RESULTS_P1,
            RESULTS_P2,
            RESULTS_P3,
    };

    private static final String RESULTS_AW1 = "<?xml version='1.0' encoding='UTF-8'?>"
                                              + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt>"
                                              + "</grandparent></g:greatgrandparent>";
    private static final String RESULTS_AW2 = "<?xml version='1.0' encoding='UTF-8'?>"
                                              + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle>ben</uncle><aunt/>"
                                              + "</grandparent></g:greatgrandparent>";
    private static final String[] RESULTS_AUNT_WRAPPED = {
            RESULTS_AW1,
            RESULTS_AW2
    };

    private static final String[] RESULTS_AUNT = {
            "<aunt xmlns:g=\"urn:g\">emma</aunt>",
            "<aunt xmlns:g=\"urn:g\"/>"
    };

    private static final String[] RESULTS_AUNT_UNWRAPPED = {
            "emma",
            ""
    };

    private static final String[] RESULTS_GRANDPARENT_TEXT = {
            "emma",
            "ben"
    };

    private static final String[] RESULTS_AUNT_AND_UNCLE = {
            "<x:uncle xmlns=\"urn:g\" xmlns:x=\"urn:x\" xmlns:c=\"urn:c\">bob</x:uncle>",
            "<x:aunt xmlns=\"urn:g\" xmlns:x=\"urn:x\" xmlns:c=\"urn:c\">emma</x:aunt>",
            "<x:uncle xmlns=\"urn:g\" xmlns:x=\"urn:x\" xmlns:c=\"urn:c\">ben</x:uncle>",
            "<x:aunt xmlns=\"urn:g\" xmlns:x=\"urn:x\" xmlns:c=\"urn:c\">jenna</x:aunt>"
    };

    private static final String[] RESULTS_NULL = {
    };

    private Map<String, String> nsmap;

    @BeforeEach
    public void setUp() {
        nsmap = new HashMap<>();
        nsmap.put("G", "urn:g");
        nsmap.put("C", "urn:c");
    }

    @Test
    public void testExtractChild() throws Exception {
        invokeAndVerify("//C:child", 'w', new ByteArrayInputStream(TEST_BODY), RESULTS_CHILD_WRAPPED);
    }

    @Test
    public void testExtractChildInjected() throws Exception {
        String[] result = RESULTS_CHILD;
        invokeAndVerify("//C:child", 'i', new ByteArrayInputStream(TEST_BODY), result);
    }

    @Test
    public void testExtractChildNSMixed() throws Exception {
        invokeAndVerify("//*:child", 'w', new ByteArrayInputStream(TEST_BODY_NS_MIXED), RESULTS_CHILD_MIXED_WRAPPED);
    }

    @Test
    public void testExtractChildNSMixedInjected() throws Exception {
        String[] result = RESULTS_CHILD_MIXED;
        invokeAndVerify("//*:child", 'i', new ByteArrayInputStream(TEST_BODY_NS_MIXED), result);
    }

    @Test
    public void testExtractAnyChild() throws Exception {
        invokeAndVerify("//*:child", 'w', new ByteArrayInputStream(TEST_BODY), RESULTS_CHILD_WRAPPED);
    }

    @Test
    public void testExtractCxxxd() throws Exception {
        String[] result = RESULTS_CHILD;
        invokeAndVerify("//C:c*d", 'i', new ByteArrayInputStream(TEST_BODY), result);
    }

    @Test
    public void testExtractUnqualifiedChild() throws Exception {
        invokeAndVerify("//child", 'w', new ByteArrayInputStream(TEST_BODY), RESULTS_NULL);
    }

    @Test
    public void testExtractSomeUnqualifiedChild() throws Exception {
        invokeAndVerify("//child", 'w', new ByteArrayInputStream(TEST_BODY_NO_NS_MIXED), RESULTS_CHILD_NO_NS_MIXED_WRAPPED);
    }

    @Test
    public void testExtractSomeUnqualifiedChildInjected() throws Exception {
        String[] result = RESULTS_CHILD_NO_NS_MIXED;
        invokeAndVerify("//child", 'i', new ByteArrayInputStream(TEST_BODY_NO_NS_MIXED), result);
    }

    @Test
    public void testExtractSomeQualifiedChild() throws Exception {
        nsmap.put("", "urn:c");
        invokeAndVerify("//child", 'w', new ByteArrayInputStream(TEST_BODY_NO_NS_MIXED), RESULTS_CHILD_NS_MIXED_WRAPPED);
    }

    @Test
    public void testExtractSomeQualifiedChildInjected() throws Exception {
        nsmap.put("", "urn:c");
        String[] result = RESULTS_CHILD_NS_MIXED;
        invokeAndVerify("//child", 'i', new ByteArrayInputStream(TEST_BODY_NO_NS_MIXED), result);
    }

    @Test
    public void testExtractWithNullNamespaceMap() throws Exception {
        nsmap = null;
        String[] result = RESULTS_CHILD_NO_NS_MIXED;
        invokeAndVerify("//child", 'i', new ByteArrayInputStream(TEST_BODY_NO_NS_MIXED), result);
    }

    @Test
    public void testExtractChildWithAncestorGGPdGP() throws Exception {
        invokeAndVerify("/G:greatgrandparent/grandparent//C:child",
                'w', new ByteArrayInputStream(TEST_BODY), RESULTS_CHILD_WRAPPED);
    }

    @Test
    public void testExtractChildWithAncestorGGPdP() throws Exception {
        invokeAndVerify("/G:greatgrandparent//C:parent/C:child",
                'w', new ByteArrayInputStream(TEST_BODY), RESULTS_CHILD_WRAPPED);
    }

    @Test
    public void testExtractChildWithAncestorGPddP() throws Exception {
        invokeAndVerify("//grandparent//C:parent/C:child",
                'w', new ByteArrayInputStream(TEST_BODY), RESULTS_CHILD_WRAPPED);
    }

    @Test
    public void testExtractChildWithAncestorGPdP() throws Exception {
        invokeAndVerify("//grandparent/C:parent/C:child",
                'w', new ByteArrayInputStream(TEST_BODY), RESULTS_CHILD_WRAPPED);
    }

    @Test
    public void testExtractChildWithAncestorP() throws Exception {
        invokeAndVerify("//C:parent/C:child",
                'w', new ByteArrayInputStream(TEST_BODY), RESULTS_CHILD_WRAPPED);
    }

    @Test
    public void testExtractChildWithAncestorGGPdGPdP() throws Exception {
        invokeAndVerify("/G:greatgrandparent/grandparent/C:parent/C:child",
                'w', new ByteArrayInputStream(TEST_BODY), RESULTS_CHILD_WRAPPED);
    }

    @Test
    public void testExtractParent() throws Exception {
        invokeAndVerify("//C:parent",
                'w', new ByteArrayInputStream(TEST_BODY), RESULTS_PARENT_WRAPPED);
    }

    @Test
    public void testExtractParentInjected() throws Exception {
        invokeAndVerify("//C:parent",
                'i', new ByteArrayInputStream(TEST_BODY), RESULTS_PARENT);
    }

    @Test
    public void testExtractAuntWC1() throws Exception {
        invokeAndVerify("//a*t",
                'w', new ByteArrayInputStream(TEST_BODY), RESULTS_AUNT_WRAPPED);
    }

    @Test
    public void testExtractAuntWC2() throws Exception {
        invokeAndVerify("//au?t",
                'w', new ByteArrayInputStream(TEST_BODY), RESULTS_AUNT_WRAPPED);
    }

    @Test
    public void testExtractAunt() throws Exception {
        invokeAndVerify("//aunt",
                'w', new ByteArrayInputStream(TEST_BODY), RESULTS_AUNT_WRAPPED);
    }

    @Test
    public void testExtractAuntInjected() throws Exception {
        invokeAndVerify("//aunt",
                'i', new ByteArrayInputStream(TEST_BODY), RESULTS_AUNT);
    }

    @Test
    public void testExtractAuntUnwrapped() throws Exception {
        invokeAndVerify("//aunt",
                'u', new ByteArrayInputStream(TEST_BODY), RESULTS_AUNT_UNWRAPPED);
    }

    @Test
    public void testExtractGrandParentText() throws Exception {
        invokeAndVerify("//grandparent",
                't', new ByteArrayInputStream(TEST_BODY), RESULTS_GRANDPARENT_TEXT);
    }

    @Test
    public void testExtractAuntAndUncleByNamespace() throws Exception {
        nsmap.put("X", "urn:x");
        invokeAndVerify("//G:grandparent/X:*",
                'i', new ByteArrayInputStream(TEST_BODY_MIXED_CHILDREN), RESULTS_AUNT_AND_UNCLE);
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
