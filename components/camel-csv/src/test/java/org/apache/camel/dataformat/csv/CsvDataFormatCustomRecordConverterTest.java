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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.camel.util.CastUtils;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Test cases for {@link CsvRecordConverter}.
 */
public class CsvDataFormatCustomRecordConverterTest extends CamelSpringTestSupport {

    @Test
    public void unmarshalTest() throws InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:unmarshaled");
        mock.expectedMessageCount(1);
        template.sendBody("direct:unmarshal", getData());
        mock.assertIsSatisfied();
        Message message = mock.getReceivedExchanges().get(0).getIn();
        List<List<String>> body = CastUtils.cast((List)message.getBody());
        assertNotNull(body);
        assertEquals(body.size(), 1);
        List<String> row = body.get(0);
        assertEquals(row.size(), 3);
        assertEquals(row.toString(), "[Hello, Again, Democracy]");
    }

    private String getData() {
        return Stream.of("A1", "B1", "C1").collect(Collectors.joining(";"));
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                                                  "org/apache/camel/dataformat/csv/CsvDataFormatCustomRecordConverter.xml");
    }
}
