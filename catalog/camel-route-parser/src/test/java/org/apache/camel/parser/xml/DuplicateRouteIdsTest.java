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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.parser.XmlRouteParser;
import org.apache.camel.parser.model.CamelRouteDetails;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuplicateRouteIdsTest {

    private static final Logger LOG = LoggerFactory.getLogger(DuplicateRouteIdsTest.class);

    @Test
    public void testXml() throws Exception {
        List<CamelRouteDetails> list = new ArrayList<>();

        InputStream is = new FileInputStream("src/test/resources/org/apache/camel/parser/xml/myduplicateroutes.xml");
        String fqn = "src/test/resources/org/apache/camel/camel/parser/xml/myduplicateroutes.xml";
        String baseDir = "src/test/resources";
        XmlRouteParser.parseXmlRouteRouteIds(is, baseDir, fqn, list);

        for (CamelRouteDetails detail : list) {
            LOG.info(detail.getRouteId());
        }

        Assert.assertEquals(3, list.size());
        Assert.assertEquals("foo", list.get(0).getRouteId());
        Assert.assertEquals("bar", list.get(1).getRouteId());
        Assert.assertEquals("foo", list.get(2).getRouteId());
    }

}
