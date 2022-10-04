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

import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Validator;

import io.atlasmap.xml.core.schema.AtlasXmlSchemaSetParser;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AtlasMapJsonToXmlSchemaTest extends CamelSpringTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(AtlasMapJsonToXmlSchemaTest.class);

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("context/AtlasMapJsonToXmlSchemaTest-context.xml");
    }

    @Test
    public void test() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.setExpectedCount(1);

        final ProducerTemplate producerTemplate = context.createProducerTemplate();
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        InputStream payloadIs = tccl.getResourceAsStream("json-source.json");
        producerTemplate.sendBody("direct:start", payloadIs);

        MockEndpoint.assertIsSatisfied(context);
        final String body = result.getExchanges().get(0).getIn().getBody(String.class);
        assertNotNull(body);
        LOG.debug(">>>>> {}", body);

        InputStream schemaIs = tccl.getResourceAsStream("xml-target-schemaset.xml");
        AtlasXmlSchemaSetParser schemaParser = new AtlasXmlSchemaSetParser(tccl);
        Validator validator = schemaParser.createSchema(schemaIs).newValidator();
        StreamSource source = new StreamSource(new StringReader(body));
        validator.validate(source);
    }

}
