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
package org.apache.camel.component.schematron.processor;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;

import net.sf.saxon.TransformerFactoryImpl;
import org.apache.camel.component.schematron.constant.Constants;
import org.junit.Assert;
import org.junit.Test;

/**
 * TemplateFactory Unit Test.
 *
 */
public class TemplatesFactoryTest {

    private String rules = "sch/schematron-1.sch";

    @Test
    public void testInstantiateAnInstanceOfTemplates() throws Exception {
        TemplatesFactory fac = TemplatesFactory.newInstance();
        TransformerFactory factory = new TransformerFactoryImpl();
        factory.setURIResolver(new ClassPathURIResolver(Constants.SCHEMATRON_TEMPLATES_ROOT_DIR, null));
        Templates templates = fac.getTemplates(ClassLoader.getSystemResourceAsStream(rules), factory);
        Assert.assertNotNull(templates);

    }
}
