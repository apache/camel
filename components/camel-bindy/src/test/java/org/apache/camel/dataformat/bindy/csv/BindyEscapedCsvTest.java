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
package org.apache.camel.dataformat.bindy.csv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.Format;
import org.apache.camel.dataformat.bindy.annotation.BindyConverter;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BindyEscapedCsvTest extends CamelTestSupport {

    @EndpointInject("mock:resultUnmarshal")
    private MockEndpoint mockEndPointUnmarshal;

    @Test
    public void testUnMarshallEscapedMessage() throws Exception {
        mockEndPointUnmarshal.expectedMessageCount(1);

        String body = """
                #a,"b",c,d,e,f,g,h,i,j,k,l,m,n,o,p,q,r,s,t,u,v,w,y
                AA-01L,"Android,10,0",3,4,"test,1,2,hello",5,6,7,8,9,100,0,0,0,0,0,0,0,0,0,0,1,0,0,0
                """;
        template.sendBody("direct:startUnmarshal", body);

        MockEndpoint.assertIsSatisfied(context);

        CsvRecordModel csvRecordModel
                = mockEndPointUnmarshal.getReceivedExchanges().get(0).getIn().getBody(CsvRecordModel.class);

        Assertions.assertEquals("AA-01L", csvRecordModel.a);
        Assertions.assertEquals("Android,10,0", csvRecordModel.b);
        Assertions.assertEquals("3", csvRecordModel.c);
        Assertions.assertEquals("test,1,2,hello", csvRecordModel.e);
        Assertions.assertEquals("5", csvRecordModel.f);
        Assertions.assertEquals("6", csvRecordModel.g);
        Assertions.assertTrue(csvRecordModel.list.contains("100"));
        Assertions.assertTrue(csvRecordModel.list.contains("7"));
        Assertions.assertTrue(csvRecordModel.list.contains("1"));
        Assertions.assertTrue(csvRecordModel.list.contains("0"));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                BindyCsvDataFormat camelDataFormat1 = new BindyCsvDataFormat(CsvRecordModel.class);

                from("direct:startUnmarshal")
                        .unmarshal(camelDataFormat1)
                        .process(exchange -> {
                            CsvRecordModel model = exchange.getIn().getBody(CsvRecordModel.class);

                            if (model != null) {
                                String a = model.getA();
                                String b = model.getB();
                                String c = model.getC();
                                String d = model.getD();
                                String e = model.getE();
                                String f = model.getF();
                                String g = model.getG();
                                List<String> list = model.getList();

                                log.info("a : \"{}\"", a);
                                log.info("b : \"{}\"", b);
                                log.info("c : \"{}\"", c);
                                log.info("d : \"{}\"", d);
                                log.info("e : \"{}\"", e);
                                log.info("f : \"{}\"", f);
                                log.info("g : \"{}\"", g);
                                log.info("list:");
                                list = list == null ? new ArrayList<>(0) : list;
                                list.forEach(id -> {
                                    log.info("\tid: \"{}\"", id);
                                });
                            }
                        })
                        .to("mock:resultUnmarshal");
            }
        };
    }

    @CsvRecord(separator = ",", autospanLine = true, skipFirstLine = true)
    public static class CsvRecordModel {

        @DataField(pos = 1)
        private String a;

        @DataField(pos = 2)
        private String b;

        @DataField(pos = 3)
        private String c;

        @DataField(pos = 4)
        private String d;

        @DataField(pos = 5)
        private String e;

        @DataField(pos = 6)
        private String f;

        @DataField(pos = 7)
        private String g;

        @DataField(pos = 8)
        @BindyConverter(AppIdentificationConvrtter.class)
        private List<String> list;

        public CsvRecordModel() {

        }

        public static class AppIdentificationConvrtter implements Format<List<String>> {

            private static final String SEPARATOR = ",";

            @Override
            public String format(List<String> object) throws Exception {
                return String.join(SEPARATOR, object);
            }

            @Override
            public List<String> parse(String string) throws Exception {
                return Arrays.asList((string == null ? "" : string).split(SEPARATOR, -1));
            }

        }

        public String getA() {
            return a;
        }

        public void setA(String a) {
            this.a = a;
        }

        public String getB() {
            return b;
        }

        public void setB(String b) {
            this.b = b;
        }

        public String getC() {
            return c;
        }

        public void setC(String c) {
            this.c = c;
        }

        public String getD() {
            return d;
        }

        public void setD(String d) {
            this.d = d;
        }

        public String getE() {
            return e;
        }

        public void setE(String e) {
            this.e = e;
        }

        public String getF() {
            return f;
        }

        public void setF(String f) {
            this.f = f;
        }

        public String getG() {
            return g;
        }

        public void setG(String g) {
            this.g = g;
        }

        public List<String> getList() {
            return list;
        }

        public void setList(List<String> list) {
            this.list = list;
        }
    }
}
