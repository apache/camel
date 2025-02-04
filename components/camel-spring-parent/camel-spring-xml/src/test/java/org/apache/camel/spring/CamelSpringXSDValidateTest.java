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
package org.apache.camel.spring;

import org.xml.sax.SAXParseException;

import org.apache.camel.util.xml.BytesSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.xml.validation.XmlValidator;
import org.springframework.xml.validation.XmlValidatorFactory;

public class CamelSpringXSDValidateTest {

    @Test
    public void testValidateXSD() throws Exception {
        Resource r = new ClassPathResource("camel-spring.xsd");
        Resource r2 = new ClassPathResource("org/springframework/beans/factory/xml/spring-beans.xsd");
        XmlValidator val = XmlValidatorFactory.createValidator(new Resource[] { r, r2 }, XmlValidatorFactory.SCHEMA_W3C_XML);

        Resource r3 = new ClassPathResource("org/apache/camel/spring/processor/choice.xml");
        SAXParseException[] err = val.validate(new BytesSource(r3.getContentAsByteArray()));
        Assertions.assertEquals(0, err.length);
    }
}
