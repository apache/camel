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

import org.w3c.dom.Element;

import org.apache.camel.parser.helper.CamelXmlHelper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertNotNull;

public class FindElementInRoutesTest {

    private static final Logger LOG = LoggerFactory.getLogger(FindElementInRoutesTest.class);

    @Test
    public void testXml() throws Exception {
        InputStream is = new FileInputStream("src/test/resources/org/apache/camel/parser/xml/myroutes.xml");
        String key = "_camelContext1/cbr-route/_from1";
        Element element = CamelXmlHelper.getSelectedCamelElementNode(key, is);
        assertNotNull("Could not find Element for key " + key, element);

        LOG.info("Found element " + element.getTagName());
    }

}
