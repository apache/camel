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

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.schematron.contant.Constants;
import org.apache.camel.component.schematron.engine.TemplatesFactory;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;
import javax.xml.transform.Templates;

/**
 * Schematron Producer Unit Test.
 * <p/>
 * Created by akhettar on 31/12/2013.
 */
public class SchematronProducerTest extends CamelTestSupport {

    private static SchematronProducer producer;

    @BeforeClass
    public static void setUP() {
        SchematronEndpoint endpoint = new SchematronEndpoint();
        Templates templates = TemplatesFactory.newInstance().newTemplates(ClassLoader.
                getSystemResourceAsStream("sch/sample-schematron.sch"));
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
