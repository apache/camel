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
package org.apache.camel.component.atlasmap;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Validator;

import io.atlasmap.xml.core.schema.AtlasXmlSchemaSetParser;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AtlasMapForceReloadTest extends CamelSpringTestSupport {

    private static final String INPUT_FILE = "json-source.json";
    private static final String EXPECTED_BODY = "{\"order\":{\"orderId\":\"123\"}}";

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("context/AtlasMapForceReloadTest-context.xml");
    }

    @Test
    public void testForceReloadDisabled() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.setExpectedCount(1);

        Files.copy(Paths.get("target/test-classes/adm/atlasmap-mapping.adm"),
                Paths.get("target/test-classes/atlasmap-mapping-force-reload.adm"),
                StandardCopyOption.REPLACE_EXISTING);
        ProducerTemplate producerTemplate = context.createProducerTemplate();
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        producerTemplate.sendBody("direct:start", tccl.getResourceAsStream(INPUT_FILE));
        MockEndpoint.assertIsSatisfied(context);
        final Object body = result.getExchanges().get(0).getIn().getBody();
        assertEquals(EXPECTED_BODY, body);

        result.reset();
        Files.copy(Paths.get("target/test-classes/adm/json-schema-source-to-xml-schema-target.adm"),
                Paths.get("target/test-classes/atlasmap-mapping-force-reload.adm"),
                StandardCopyOption.REPLACE_EXISTING);
        producerTemplate.sendBody("direct:start", tccl.getResourceAsStream(INPUT_FILE));
        MockEndpoint.assertIsSatisfied(context);
        final Object body2 = result.getExchanges().get(0).getIn().getBody();
        assertEquals(EXPECTED_BODY, body2);
    }

    @Test
    public void testForceReloadEnabled() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.setExpectedCount(1);

        Files.copy(Paths.get("target/test-classes/adm/atlasmap-mapping.adm"),
                Paths.get("target/test-classes/atlasmap-mapping-force-reload.adm"),
                StandardCopyOption.REPLACE_EXISTING);
        ProducerTemplate producerTemplate = context.createProducerTemplate();
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        producerTemplate.sendBody("direct:start-force-reload", tccl.getResourceAsStream(INPUT_FILE));
        MockEndpoint.assertIsSatisfied(context);
        final Object body = result.getExchanges().get(0).getIn().getBody();
        assertEquals(EXPECTED_BODY, body);

        result.reset();
        Files.copy(Paths.get("target/test-classes/adm/json-schema-source-to-xml-schema-target.adm"),
                Paths.get("target/test-classes/atlasmap-mapping-force-reload.adm"),
                StandardCopyOption.REPLACE_EXISTING);
        producerTemplate.sendBody("direct:start", tccl.getResourceAsStream(INPUT_FILE));
        MockEndpoint.assertIsSatisfied(context);
        final Object body2 = result.getExchanges().get(0).getIn().getBody();
        InputStream schemaIs = tccl.getResourceAsStream("xml-target-schemaset.xml");
        AtlasXmlSchemaSetParser schemaParser = new AtlasXmlSchemaSetParser(tccl);
        Validator validator = schemaParser.createSchema(schemaIs).newValidator();
        StreamSource source = new StreamSource(new StringReader(body2.toString()));
        validator.validate(source);
    }

}
