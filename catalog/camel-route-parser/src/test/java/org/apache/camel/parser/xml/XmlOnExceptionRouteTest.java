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

public class XmlOnExceptionRouteTest {

    private static final Logger LOG = LoggerFactory.getLogger(XmlOnExceptionRouteTest.class);

    @Test
    public void testXml() throws Exception {
        List<CamelEndpointDetails> endpoints = new ArrayList<>();

        InputStream is = new FileInputStream("src/test/resources/org/apache/camel/parser/xml/mycamel-onexception.xml");
        String fqn = "src/test/resources/org/apache/camel/parser/xml/mycamel-onexception.xml";
        String baseDir = "src/test/resources";
        XmlRouteParser.parseXmlRouteEndpoints(is, baseDir, fqn, endpoints);

        for (CamelEndpointDetails detail : endpoints) {
            LOG.info(detail.getEndpointUri());
        }

        Assert.assertEquals("log:all", endpoints.get(0).getEndpointUri());
        Assert.assertEquals("mock:dead", endpoints.get(1).getEndpointUri());
        Assert.assertEquals("log:done", endpoints.get(2).getEndpointUri());
        Assert.assertEquals("stream:in?promptMessage=Enter something:", endpoints.get(3).getEndpointUri());
        Assert.assertEquals("stream:out", endpoints.get(4).getEndpointUri());
    }

}
