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
package org.apache.camel.component.cxf.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.w3c.dom.Element;

import org.apache.camel.converter.jaxp.XmlConverter;
import org.junit.Assert;
import org.junit.Test;


public class CxfUtilsTest extends Assert {
    private static final String TEST_XML1 = 
        "<root><test1 id=\"1\"><test2 id=\"3\" xmlns=\"http://camel.apache.org/schema/one\">hello</test2></test1></root>";
    private static final String EXPECTED_STRING1 = "<test1 id=\"1\"><test2 xmlns=\"http://camel.apache.org/schema/one\" id=\"3\">hello</test2></test1>";        
    private static final String TEST_XML2 = 
        "<root xmlns=\"http://camel.apache.org/schema/one\" xmlns:two=\"http://camel.apache.org/schema/two\"><test1 id=\"1\"><two:test2 id=\"3\">hello</two:test2></test1></root>";
    private static final String EXPECTED_STRING2 = 
        "<test1 xmlns=\"http://camel.apache.org/schema/one\" xmlns:two=\"http://camel.apache.org/schema/two\" id=\"1\"><two:test2 id=\"3\">hello</two:test2></test1>";
    @Test
    public void testXmlToString() throws Exception {
        assertEquals("Get unexpected String", EXPECTED_STRING1, getSubElementString(TEST_XML1));
        assertEquals("Get unexpected String", EXPECTED_STRING2, getSubElementString(TEST_XML2));
    }
    
    private String getSubElementString(String string) throws Exception {
        InputStream is = new ByteArrayInputStream(string.getBytes("UTF-8"));
        XmlConverter converter = new XmlConverter();
        Element element = converter.toDOMElement(converter.toDOMSource(is, null));
        Element subElement = (Element)element.getFirstChild();
        return CxfUtils.elementToString(subElement);
        
    }

}
