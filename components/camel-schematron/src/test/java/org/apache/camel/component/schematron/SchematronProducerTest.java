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
package org.apache.camel.component.schematron;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;

import net.sf.saxon.TransformerFactoryImpl;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.schematron.constant.Constants;
import org.apache.camel.component.schematron.processor.ClassPathURIResolver;
import org.apache.camel.component.schematron.processor.TemplatesFactory;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Schematron Producer Unit Test.
 *
 */
public class SchematronProducerTest extends CamelTestSupport {

    private static SchematronProducer producer;

    @BeforeClass
    public static void setUP() {
        SchematronEndpoint endpoint = new SchematronEndpoint();
        TransformerFactory fac = new TransformerFactoryImpl();
        fac.setURIResolver(new ClassPathURIResolver(Constants.SCHEMATRON_TEMPLATES_ROOT_DIR, endpoint.getUriResolver()));
        Templates templates = TemplatesFactory.newInstance().getTemplates(ClassLoader.
                getSystemResourceAsStream("sch/schematron-1.sch"), fac);
        endpoint.setRules(templates);
        producer = new SchematronProducer(endpoint);
    }

    @Test
    public void testProcessValidXML() throws Exception {
        Exchange exc = new DefaultExchange(context, ExchangePattern.InOut);
        exc.getIn().setBody(ClassLoader.getSystemResourceAsStream("xml/article-1.xml"));

        // process xml payload
        producer.process(exc);

        // assert
        assertTrue(exc.getOut().getHeader(Constants.VALIDATION_STATUS).equals(Constants.SUCCESS));
    }

    @Test
    public void testProcessInValidXML() throws Exception {
        Exchange exc = new DefaultExchange(context, ExchangePattern.InOut);
        exc.getIn().setBody(ClassLoader.getSystemResourceAsStream("xml/article-2.xml"));

        // process xml payload
        producer.process(exc);

        // assert
        assertTrue(exc.getOut().getHeader(Constants.VALIDATION_STATUS).equals(Constants.FAILED));

    }

}
