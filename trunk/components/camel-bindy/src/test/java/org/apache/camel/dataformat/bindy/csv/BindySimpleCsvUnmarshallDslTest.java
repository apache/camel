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
package org.apache.camel.dataformat.bindy.csv;

import org.apache.camel.dataformat.bindy.CommonBindyTest;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration
public class BindySimpleCsvUnmarshallDslTest extends CommonBindyTest {

    @Test
    @DirtiesContext
    public void testUnMarshallMessage() throws Exception {

        String message = "01,,Albert,Einstein,ISIN,BE12345678,SELL,,1500,EUR,08-01-2009\r\n" + "02,A1,,Preud'Homme,ISIN,XD12345678,BUY,,2500,USD,08-01-2009\r\n"
                         + "03,A2,Jacques,,,BE12345678,SELL,,1500,EUR,08-01-2009\r\n" + "04,A3,Michel,Platini,,,BUY,,2500,USD,08-01-2009\r\n"
                         + "05,A4,Jacques,Dutronc,ISIN,BE12345678,,,1500,EUR,08-01-2009\r\n" + "06,A5,Jacques,Brel,ISIN,XD12345678,SELL,Share,,USD,08-01-2009\r\n"
                         + "07,A6,Myl" + "\uc3a8" + "ne,Farmer,ISIN,BE12345678,BUY,1500,,,08-01-2009\r\n" + "08,A7,Eva,Longoria,ISIN,XD12345678,SELL,Share,2500,USD,\r\n"
                         + ",,,D,,BE12345678,SELL,,,,08-01-2009\r\n" + ",,,D,ISIN,BE12345678,,,,,08-01-2009\r\n" + ",,,D,ISIN,LU123456789,,,,,\r\n"
                         + "10,A8,Pauline,Lafont,ISIN,XD12345678,SELL,Share,2500,USD,08-01-2009\r\n" + "10,A9,Louis,Pasteur,ISIN,XD12345678,BUY,Share,2500.45,USD,08-01-2009";

        template.sendBody(message);
        result.expectedMessageCount(1);
        result.assertIsSatisfied();
    }

    /*
     * @Configuration public static class ContextConfig extends
     * SingleRouteCamelConfiguration {
     * @Override
     * @Bean public RouteBuilder route() { return new RouteBuilder() {
     * @Override public void configure() { //
     * from("file://src/test/data?noop=true") from(URI_DIRECT_START).unmarshal()
     * .bindy(BindyType.Csv,
     * "org.apache.camel.dataformat.bindy.model.simple.oneclass")
     * .to(URI_MOCK_RESULT); } }; } }
     */
}
