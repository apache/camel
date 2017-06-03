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
package org.apache.camel.support;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import static org.apache.camel.TestSupport.getJavaMajorVersion;

/**
 *
 */
public class XMLTokenExpressionIteratorTest extends TestCase {
    private static final byte[] TEST_BODY = 
        ("<?xml version='1.0' encoding='UTF-8'?>"
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
            + "</g:greatgrandparent>").getBytes();

    // mixing a default namespace with an explicit namespace for child
    private static final byte[] TEST_BODY_NS_MIXED =
        ("<?xml version='1.0' encoding='UTF-8'?>"
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
            + "</g:greatgrandparent>").getBytes();

    // mixing a no namespace with an explicit namespace for child
    private static final byte[] TEST_BODY_NO_NS_MIXED =
        ("<?xml version='1.0' encoding='UTF-8'?>"
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
            + "</g:greatgrandparent>").getBytes();

    private static final String[] RESULTS_CHILD_WRAPPED = {
        "<?xml version='1.0' encoding='UTF-8'?>"
            + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt>"
            + "<c:parent some_attr='1' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
            + "<c:child some_attr='a' anotherAttr='a'></c:child>"
            + "</c:parent></grandparent></g:greatgrandparent>",
        "<?xml version='1.0' encoding='UTF-8'?>"
            + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt>"
            + "<c:parent some_attr='1' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
            + "<c:child some_attr='b' anotherAttr='b'/>"
            + "</c:parent></grandparent></g:greatgrandparent>",
        "<?xml version='1.0' encoding='UTF-8'?>"
            + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt>"
            + "<c:parent some_attr='2' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
            + "<c:child some_attr='c' anotherAttr='c'></c:child>"
            + "</c:parent></grandparent></g:greatgrandparent>",
        "<?xml version='1.0' encoding='UTF-8'?>"
            + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt>"
            + "<c:parent some_attr='2' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
            + "<c:child some_attr='d' anotherAttr='d'/>"
            + "</c:parent></grandparent></g:greatgrandparent>",
        "<?xml version='1.0' encoding='UTF-8'?>"
            + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle>ben</uncle><aunt/>"
            + "<c:parent some_attr='3' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
            + "<c:child some_attr='e' anotherAttr='e'></c:child>"
            + "</c:parent></grandparent></g:greatgrandparent>",
        "<?xml version='1.0' encoding='UTF-8'?>"
            + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle>ben</uncle><aunt/>"
            + "<c:parent some_attr='3' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
            + "<c:child some_attr='f' anotherAttr='f'/>"
            + "</c:parent></grandparent></g:greatgrandparent>"
    };

    private static final String[] RESULTS_CHILD_MIXED = {
        "<child some_attr='a' anotherAttr='a' xmlns=\"urn:c\" xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"></child>",
        "<x:child xmlns:x='urn:c' some_attr='b' anotherAttr='b' xmlns='urn:c' xmlns:g='urn:g' xmlns:c='urn:c'/>",
        "<child some_attr='c' anotherAttr='c' xmlns='urn:c' xmlns:g='urn:g' xmlns:c='urn:c'></child>",
        "<c:child some_attr='d' anotherAttr='d' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
    };
    
    private static final String[] RESULTS_CHILD_MIXED_JAVA8 = {
        "<child some_attr='a' anotherAttr='a' xmlns=\"urn:c\" xmlns:c=\"urn:c\" xmlns:g=\"urn:g\"></child>",
        "<x:child xmlns:x='urn:c' some_attr='b' anotherAttr='b' xmlns='urn:c' xmlns:c='urn:c' xmlns:g='urn:g'/>",
        "<child some_attr='c' anotherAttr='c' xmlns='urn:c' xmlns:g='urn:g' xmlns:c='urn:c'></child>",
        "<c:child some_attr='d' anotherAttr='d' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
    };

    private static final String[] RESULTS_CHILD_MIXED_WRAPPED = {
        "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent>"
            + "<parent some_attr='1' xmlns:c='urn:c' xmlns=\"urn:c\">"
            + "<child some_attr='a' anotherAttr='a'></child></parent></grandparent></g:greatgrandparent>",
        "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent>"
            + "<parent some_attr='1' xmlns:c='urn:c' xmlns=\"urn:c\">"
            + "<x:child xmlns:x='urn:c' some_attr='b' anotherAttr='b'/></parent></grandparent></g:greatgrandparent>",
        "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent>"
            + "<c:parent some_attr='2' xmlns:c='urn:c'>"
            + "<child some_attr='c' anotherAttr='c' xmlns='urn:c'></child></c:parent></grandparent></g:greatgrandparent>",
        "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent>"
            + "<c:parent some_attr='2' xmlns:c='urn:c'>"
            + "<c:child some_attr='d' anotherAttr='d'/></c:parent></grandparent></g:greatgrandparent>"
    };

    private static final String[] RESULTS_CHILD = {
        "<c:child some_attr='a' anotherAttr='a' xmlns:g=\"urn:g\" xmlns:d=\"urn:d\" xmlns:c=\"urn:c\"></c:child>",
        "<c:child some_attr='b' anotherAttr='b' xmlns:g=\"urn:g\" xmlns:d=\"urn:d\" xmlns:c=\"urn:c\"/>",
        "<c:child some_attr='c' anotherAttr='c' xmlns:g=\"urn:g\" xmlns:d=\"urn:d\" xmlns:c=\"urn:c\"></c:child>",
        "<c:child some_attr='d' anotherAttr='d' xmlns:g=\"urn:g\" xmlns:d=\"urn:d\" xmlns:c=\"urn:c\"/>",
        "<c:child some_attr='e' anotherAttr='e' xmlns:g=\"urn:g\" xmlns:d=\"urn:d\" xmlns:c=\"urn:c\"></c:child>",
        "<c:child some_attr='f' anotherAttr='f' xmlns:g=\"urn:g\" xmlns:d=\"urn:d\" xmlns:c=\"urn:c\"/>"
    };
    
    private static final String[] RESULTS_CHILD_JAVA8 = {
        "<c:child some_attr='a' anotherAttr='a' xmlns:c=\"urn:c\" xmlns:d=\"urn:d\" xmlns:g=\"urn:g\"></c:child>",
        "<c:child some_attr='b' anotherAttr='b' xmlns:c=\"urn:c\" xmlns:d=\"urn:d\" xmlns:g=\"urn:g\"/>",
        "<c:child some_attr='c' anotherAttr='c' xmlns:c=\"urn:c\" xmlns:d=\"urn:d\" xmlns:g=\"urn:g\"></c:child>",
        "<c:child some_attr='d' anotherAttr='d' xmlns:c=\"urn:c\" xmlns:d=\"urn:d\" xmlns:g=\"urn:g\"/>",
        "<c:child some_attr='e' anotherAttr='e' xmlns:c=\"urn:c\" xmlns:d=\"urn:d\" xmlns:g=\"urn:g\"></c:child>",
        "<c:child some_attr='f' anotherAttr='f' xmlns:c=\"urn:c\" xmlns:d=\"urn:d\" xmlns:g=\"urn:g\"/>"
    };

    private static final String[] RESULTS_CHILD_NO_NS_MIXED = {
        "<child some_attr='a' anotherAttr='a' xmlns='' xmlns:g='urn:g' xmlns:c='urn:c'></child>",
        "<child some_attr='c' anotherAttr='c' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"></child>",
    };
    
    private static final String[] RESULTS_CHILD_NO_NS_MIXED_JAVA8 = {
        "<child some_attr='a' anotherAttr='a' xmlns='' xmlns:c='urn:c' xmlns:g='urn:g'></child>",
        "<child some_attr='c' anotherAttr='c' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"></child>",
    };

    // note that there is no preceding sibling to the extracted child
    private static final String[] RESULTS_CHILD_NO_NS_MIXED_WRAPPED = {
        "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent>"
            + "<parent some_attr='1' xmlns:c='urn:c' xmlns=\"urn:c\">"
            + "<child some_attr='a' anotherAttr='a' xmlns=''></child></parent></grandparent></g:greatgrandparent>",
        "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent>"
            + "<c:parent some_attr='2' xmlns:c='urn:c'>"
            + "<child some_attr='c' anotherAttr='c'></child></c:parent></grandparent></g:greatgrandparent>",
    };

    private static final String[] RESULTS_CHILD_NS_MIXED = {
        "<x:child xmlns:x='urn:c' some_attr='b' anotherAttr='b' xmlns='urn:c' xmlns:g='urn:g' xmlns:c='urn:c'/>",
        "<c:child some_attr='d' anotherAttr='d' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
    };
    
    private static final String[] RESULTS_CHILD_NS_MIXED_JAVA8 = {
        "<x:child xmlns:x='urn:c' some_attr='b' anotherAttr='b' xmlns='urn:c' xmlns:c='urn:c' xmlns:g='urn:g'/>",
        "<c:child some_attr='d' anotherAttr='d' xmlns:g=\"urn:g\" xmlns:c=\"urn:c\"/>"
    };

    // note that there is a preceding sibling to the extracted child
    private static final String[] RESULTS_CHILD_NS_MIXED_WRAPPED = {
        "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent>"
            + "<parent some_attr='1' xmlns:c='urn:c' xmlns=\"urn:c\">"
            + "<child some_attr='a' anotherAttr='a' xmlns=''></child>"
            + "<x:child xmlns:x='urn:c' some_attr='b' anotherAttr='b'/></parent></grandparent></g:greatgrandparent>",
        "<?xml version='1.0' encoding='UTF-8'?><g:greatgrandparent xmlns:g='urn:g'><grandparent>"
            + "<c:parent some_attr='2' xmlns:c='urn:c'>"
            + "<child some_attr='c' anotherAttr='c'></child>"
            + "<c:child some_attr='d' anotherAttr='d'/></c:parent></grandparent></g:greatgrandparent>"
    };

    private static final String[] RESULTS_PARENT_WRAPPED = {
        "<?xml version='1.0' encoding='UTF-8'?>"
            + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt>"
            + "<c:parent some_attr='1' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
            + "<c:child some_attr='a' anotherAttr='a'></c:child>"
            + "<c:child some_attr='b' anotherAttr='b'/>"
            + "</c:parent></grandparent></g:greatgrandparent>",
        "<?xml version='1.0' encoding='UTF-8'?>"
            + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt>"
            + "<c:parent some_attr='2' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
            + "<c:child some_attr='c' anotherAttr='c'></c:child>"
            + "<c:child some_attr='d' anotherAttr='d'/>"
            + "</c:parent></grandparent></g:greatgrandparent>",
        "<?xml version='1.0' encoding='UTF-8'?>"
            + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle>ben</uncle><aunt/>"
            + "<c:parent some_attr='3' xmlns:c='urn:c' xmlns:d=\"urn:d\">"
            + "<c:child some_attr='e' anotherAttr='e'></c:child>"
            + "<c:child some_attr='f' anotherAttr='f'/>"
            + "</c:parent></grandparent></g:greatgrandparent>",
    };
    
    private static final String[] RESULTS_PARENT = {
        "<c:parent some_attr='1' xmlns:c='urn:c' xmlns:d=\"urn:d\" xmlns:g='urn:g'>"
            + "<c:child some_attr='a' anotherAttr='a'></c:child>"
            + "<c:child some_attr='b' anotherAttr='b'/>"
            + "</c:parent>",
        "<c:parent some_attr='2' xmlns:c='urn:c' xmlns:d=\"urn:d\" xmlns:g='urn:g'>"
            + "<c:child some_attr='c' anotherAttr='c'></c:child>"
            + "<c:child some_attr='d' anotherAttr='d'/>"
            + "</c:parent>",
        "<c:parent some_attr='3' xmlns:c='urn:c' xmlns:d=\"urn:d\" xmlns:g='urn:g'>"
            + "<c:child some_attr='e' anotherAttr='e'></c:child>"
            + "<c:child some_attr='f' anotherAttr='f'/>"
            + "</c:parent>",
    };
    
    private static final String[] RESULTS_AUNT_WRAPPED = {
        "<?xml version='1.0' encoding='UTF-8'?>"
            + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle/><aunt>emma</aunt>"
            + "</grandparent></g:greatgrandparent>",
        "<?xml version='1.0' encoding='UTF-8'?>"
            + "<g:greatgrandparent xmlns:g='urn:g'><grandparent><uncle>ben</uncle><aunt/>"
            + "</grandparent></g:greatgrandparent>"
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

    private static final String[] RESULTS_NULL = {
    };
 

    private Map<String, String> nsmap;

    @Override
    protected void setUp() throws Exception {
        nsmap = new HashMap<String, String>();
        nsmap.put("G", "urn:g");
        nsmap.put("C", "urn:c");
    }


    public void testExtractChild() throws Exception {
        invokeAndVerify("//C:child", 'w', new ByteArrayInputStream(TEST_BODY), RESULTS_CHILD_WRAPPED);
    }

    public void testExtractChildInjected() throws Exception {
        String[] result = RESULTS_CHILD_JAVA8;
        if (isJava7OrLower()) {
            result = RESULTS_CHILD;
        }
        invokeAndVerify("//C:child", 'i', new ByteArrayInputStream(TEST_BODY), result);
    }

    public void testExtractChildNSMixed() throws Exception {
        invokeAndVerify("//*:child", 'w', new ByteArrayInputStream(TEST_BODY_NS_MIXED), RESULTS_CHILD_MIXED_WRAPPED);
    }

    public void testExtractChildNSMixedInjected() throws Exception {
        String[] result = RESULTS_CHILD_MIXED_JAVA8;
        if (isJava7OrLower()) {
            result = RESULTS_CHILD_MIXED;
        }
        invokeAndVerify("//*:child", 'i', new ByteArrayInputStream(TEST_BODY_NS_MIXED), result);
    }

    public void testExtractAnyChild() throws Exception {
        invokeAndVerify("//*:child", 'w', new ByteArrayInputStream(TEST_BODY), RESULTS_CHILD_WRAPPED);
    }

    public void testExtractCxxxd() throws Exception {
        String[] result = RESULTS_CHILD_JAVA8;
        if (isJava7OrLower()) {
            result = RESULTS_CHILD;
        }
        invokeAndVerify("//C:c*d", 'i', new ByteArrayInputStream(TEST_BODY), result);
    }

    public void testExtractUnqualifiedChild() throws Exception {
        invokeAndVerify("//child", 'w', new ByteArrayInputStream(TEST_BODY), RESULTS_NULL);
    }

    public void testExtractSomeUnqualifiedChild() throws Exception {
        invokeAndVerify("//child", 'w', new ByteArrayInputStream(TEST_BODY_NO_NS_MIXED), RESULTS_CHILD_NO_NS_MIXED_WRAPPED);
    }

    public void testExtractSomeUnqualifiedChildInjected() throws Exception {
        String[] result = RESULTS_CHILD_NO_NS_MIXED_JAVA8;
        if (isJava7OrLower())  {
            result = RESULTS_CHILD_NO_NS_MIXED;
        }
        invokeAndVerify("//child", 'i', new ByteArrayInputStream(TEST_BODY_NO_NS_MIXED), result);
    }

    public void testExtractSomeQualifiedChild() throws Exception {
        nsmap.put("", "urn:c");
        invokeAndVerify("//child", 'w', new ByteArrayInputStream(TEST_BODY_NO_NS_MIXED), RESULTS_CHILD_NS_MIXED_WRAPPED);
    }

    public void testExtractSomeQualifiedChildInjected() throws Exception {
        nsmap.put("", "urn:c");
        String[] result = RESULTS_CHILD_NS_MIXED_JAVA8;
        if (isJava7OrLower()) {
            result = RESULTS_CHILD_NS_MIXED;
        }
        invokeAndVerify("//child", 'i', new ByteArrayInputStream(TEST_BODY_NO_NS_MIXED), result);
    }

    public void testExtractWithNullNamespaceMap() throws Exception {
        nsmap = null;
        String[] result = RESULTS_CHILD_NO_NS_MIXED_JAVA8;
        if (isJava7OrLower()) {
            result = RESULTS_CHILD_NO_NS_MIXED;
        }
        invokeAndVerify("//child", 'i', new ByteArrayInputStream(TEST_BODY_NO_NS_MIXED), result);
    }

    public void testExtractChildWithAncestorGGPdGP() throws Exception {
        invokeAndVerify("/G:greatgrandparent/grandparent//C:child", 
                        'w', new ByteArrayInputStream(TEST_BODY), RESULTS_CHILD_WRAPPED);
    }

    public void testExtractChildWithAncestorGGPdP() throws Exception {
        invokeAndVerify("/G:greatgrandparent//C:parent/C:child", 
                        'w', new ByteArrayInputStream(TEST_BODY), RESULTS_CHILD_WRAPPED);
    }

    public void testExtractChildWithAncestorGPddP() throws Exception {
        invokeAndVerify("//grandparent//C:parent/C:child", 
                        'w', new ByteArrayInputStream(TEST_BODY), RESULTS_CHILD_WRAPPED);
    }

    public void testExtractChildWithAncestorGPdP() throws Exception {
        invokeAndVerify("//grandparent/C:parent/C:child", 
                        'w', new ByteArrayInputStream(TEST_BODY), RESULTS_CHILD_WRAPPED);
    }

    public void testExtractChildWithAncestorP() throws Exception {
        invokeAndVerify("//C:parent/C:child", 
                        'w', new ByteArrayInputStream(TEST_BODY), RESULTS_CHILD_WRAPPED);
    }

    public void testExtractChildWithAncestorGGPdGPdP() throws Exception {
        invokeAndVerify("/G:greatgrandparent/grandparent/C:parent/C:child", 
                        'w', new ByteArrayInputStream(TEST_BODY), RESULTS_CHILD_WRAPPED);
    }
    
    public void testExtractParent() throws Exception {
        invokeAndVerify("//C:parent", 
                        'w', new ByteArrayInputStream(TEST_BODY), RESULTS_PARENT_WRAPPED);
    }
    
    public void testExtractParentInjected() throws Exception {
        invokeAndVerify("//C:parent", 
                        'i', new ByteArrayInputStream(TEST_BODY), RESULTS_PARENT);
    }
    
    public void testExtractAuntWC1() throws Exception {
        invokeAndVerify("//a*t", 
                        'w', new ByteArrayInputStream(TEST_BODY), RESULTS_AUNT_WRAPPED);
    }

    public void testExtractAuntWC2() throws Exception {
        invokeAndVerify("//au?t", 
                        'w', new ByteArrayInputStream(TEST_BODY), RESULTS_AUNT_WRAPPED);
    }

    public void testExtractAunt() throws Exception {
        invokeAndVerify("//aunt", 
                        'w', new ByteArrayInputStream(TEST_BODY), RESULTS_AUNT_WRAPPED);
    }

    public void testExtractAuntInjected() throws Exception {
        invokeAndVerify("//aunt", 
                        'i', new ByteArrayInputStream(TEST_BODY), RESULTS_AUNT);
    }

    public void testExtractAuntUnwrapped() throws Exception {
        invokeAndVerify("//aunt", 
                        'u', new ByteArrayInputStream(TEST_BODY), RESULTS_AUNT_UNWRAPPED);
    }

    public void testExtractGrandParentText() throws Exception {
        invokeAndVerify("//grandparent", 
                        't', new ByteArrayInputStream(TEST_BODY), RESULTS_GRANDPARENT_TEXT);
    }

    private void invokeAndVerify(String path, char mode, InputStream in, String[] expected) throws Exception {
        XMLTokenExpressionIterator xtei = new XMLTokenExpressionIterator(path, mode);
        xtei.setNamespaces(nsmap);
        
        Iterator<?> it = xtei.createIterator(in, "utf-8");
        List<String> results = new ArrayList<String>();
        while (it.hasNext()) {
            results.add((String)it.next());
        }
        ((Closeable)it).close();

        assertEquals("token count", expected.length, results.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("mismatch [" + i + "]", expected[i], results.get(i));
        }
    }

    private boolean isJava7OrLower() {
        return getJavaMajorVersion() <= 7;
    }
}
