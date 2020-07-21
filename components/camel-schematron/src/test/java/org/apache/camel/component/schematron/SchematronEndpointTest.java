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
package org.apache.camel.component.schematron;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.component.schematron.constant.Constants;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

/**
 * Unit test for SchematronEndpoint.
 *
 */
public class SchematronEndpointTest extends CamelTestSupport {


    @Test
    public void testSchematronFileReadFromClassPath()throws Exception {

        String payload = IOUtils.toString(ClassLoader.getSystemResourceAsStream("xml/article-1.xml"));
        Endpoint endpoint = context().getEndpoint("schematron://sch/schematron-1.sch");
        Producer producer = endpoint.createProducer();
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);

        exchange.getIn().setBody(payload);

        // invoke the component.
        producer.process(exchange);

        String report = exchange.getMessage().getHeader(Constants.VALIDATION_REPORT, String.class);
        assertNotNull(report);
    }

    @Test
    public void testSchematronFileReadFromFileSystem()throws Exception {

        String payload = IOUtils.toString(ClassLoader.getSystemResourceAsStream("xml/article-2.xml"));
        String path = ClassLoader.getSystemResource("sch/schematron-1.sch").getPath();
        Endpoint endpoint = context().getEndpoint("schematron://" + path);
        Producer producer = endpoint.createProducer();
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);

        exchange.getIn().setBody(payload);

        // invoke the component.
        producer.process(exchange);

        String report = exchange.getMessage().getHeader(Constants.VALIDATION_REPORT, String.class);
        assertNotNull(report);
    }

}
