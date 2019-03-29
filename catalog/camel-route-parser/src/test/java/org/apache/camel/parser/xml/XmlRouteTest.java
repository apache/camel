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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.parser.XmlRouteParser;
import org.apache.camel.parser.model.CamelEndpointDetails;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmlRouteTest {

    private static final Logger LOG = LoggerFactory.getLogger(XmlRouteTest.class);

    @Test
    public void testXml() throws Exception {
        test("mycamel", 29);
    }

    @Test
    public void testXmlWithNamespacePrefix() throws Exception {
        test("mycamel-withNamespacePrefix", 51);
    }

    private void test(String filename, int pos) throws Exception {
        List<CamelEndpointDetails> endpoints = new ArrayList<>();

        InputStream is = new FileInputStream("src/test/resources/org/apache/camel/parser/xml/" + filename + ".xml");
        String fqn = "src/test/resources/org/apache/camel/camel/parser/xml/" + filename + ".xml";
        String baseDir = "src/test/resources";
        XmlRouteParser.parseXmlRouteEndpoints(is, baseDir, fqn, endpoints);

        for (CamelEndpointDetails detail : endpoints) {
            LOG.info(detail.getEndpointUri());
        }
        Assert.assertEquals("stream:in?promptMessage=Enter something:", endpoints.get(0).getEndpointUri());
        Assert.assertEquals("stream:out", endpoints.get(1).getEndpointUri());
        Assert.assertEquals("39", endpoints.get(1).getLineNumber());
        Assert.assertEquals(pos, endpoints.get(1).getLinePosition());
    }

}
