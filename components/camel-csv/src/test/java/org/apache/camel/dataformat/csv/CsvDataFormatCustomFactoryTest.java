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
package org.apache.camel.dataformat.csv;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CsvDataFormatCustomFactoryTest extends CamelSpringTestSupport {
    @Test
    public void marshalTest() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:marshaled");
        mock.expectedMessageCount(1);

        template.sendBody("direct:marshal", getData());

        mock.assertIsSatisfied();

        String body = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        String[] lines = body.split("\r\n");

        Assert.assertEquals(2, lines.length);
        Assert.assertEquals("A1:B1:C1", lines[0].trim());
        Assert.assertEquals("A2:B2:C2", lines[1].trim());
    }

    private List<List<String>> getData() {
        return Arrays.asList(
            Arrays.asList("A1", "B1", "C1"),
            Arrays.asList("A2", "B2", "C2")
        );
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/dataformat/csv/CsvDataFormatCustomFactoryTest-context.xml");
    }
}
