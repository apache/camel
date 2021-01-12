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
package org.apache.camel.main.xml;

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.main.Main;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.rest.GetVerbDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.VerbDefinition;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainXmlRestsTest {

    @Test
    public void testMainRestsCollector() throws Exception {
        // will load XML from target/classes when testing
        doTestMain("org/apache/camel/main/xml/camel-rests.xml");
    }

    @Test
    public void testMainRestsCollectorScan() throws Exception {
        // will load XML from target/classes when testing
        doTestMain("org/apache/camel/main/xml/camel-res*.xml");
    }

    @Test
    public void testMainRestsCollectorScanWildcardDirClasspathPath() throws Exception {
        // will load XML from target/classes when testing
        doTestMain("org/apache/camel/main/**/camel-res*.xml");
    }

    @Test
    public void testMainRestsCollectorScanClasspathPrefix() throws Exception {
        // will load XML from target/classes when testing
        doTestMain("classpath:org/apache/camel/main/xml/camel-res*.xml");
    }

    @Test
    public void testMainRestsCollectorScanInDir() throws Exception {
        doTestMain("file:src/test/resources/org/apache/camel/main/xml/camel-res*.xml");
    }

    @Test
    public void testMainRestsCollectorScanWildcardDirFilePath() throws Exception {
        doTestMain("file:src/test/resources/**/camel-res*.xml");
    }

    @Test
    public void testMainRestsCollectorFile() throws Exception {
        doTestMain(
                "file:src/test/resources/org/apache/camel/main/xml/camel-rests.xml,");
    }

    protected void doTestMain(String xmlRests) throws Exception {
        Main main = new Main();
        main.bind("restConsumerFactory", new MockRestConsumerFactory());
        main.configure().withXmlRests(xmlRests);
        main.start();

        CamelContext camelContext = main.getCamelContext();
        assertNotNull(camelContext);

        List<RestDefinition> restDefinitions = camelContext.adapt(ModelCamelContext.class).getRestDefinitions();
        assertEquals(1, restDefinitions.size());

        RestDefinition restDefinition = restDefinitions.get(0);
        assertEquals("bar", restDefinition.getId());
        assertEquals("/say/hello", restDefinition.getPath());

        List<VerbDefinition> verbs = restDefinition.getVerbs();
        assertNotNull(verbs);
        assertEquals(1, verbs.size());

        VerbDefinition verbDefinition = verbs.get(0);
        assertTrue(verbDefinition instanceof GetVerbDefinition);

        main.stop();
    }

    private static final class MockRestConsumerFactory implements RestConsumerFactory {
        @Override
        public Consumer createConsumer(
                CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                String consumes,
                String produces, RestConfiguration configuration, Map<String, Object> parameters)
                throws Exception {
            return null;
        }
    }
}
