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
package org.apache.camel.dataformat.parquet.avro;

import java.util.Collection;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class SpringParquetAvroDataFormatRouteTest extends CamelSpringTestSupport {

    @Test
    public void testSpringMarshalAndUnmarshalMap() throws Exception {
        Collection<Pojo> in = List.of(
                new Pojo(1, "airport"),
                new Pojo(2, "penguin"),
                new Pojo(3, "verb"));
        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.message(0).body().isEqualTo(in);

        Object marshalled = template.requestBody("direct:marshal", in);
        template.sendBody("direct:unmarshal", marshalled);
        mock.assertIsSatisfied();
        List<Exchange> receivedExchanges = mock.getReceivedExchanges();
        assertArrayEquals(in.toArray(), receivedExchanges.get(0).getIn().getBody(List.class).toArray());
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("SpringParquetAvroDataFormatRouteTest.xml");
    }

}
