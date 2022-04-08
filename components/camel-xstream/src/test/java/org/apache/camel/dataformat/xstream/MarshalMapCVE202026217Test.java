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
package org.apache.camel.dataformat.xstream;

import java.util.HashMap;

import com.thoughtworks.xstream.security.ForbiddenClassException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Marshal tests with Map. Related to https://x-stream.github.io/CVE-2020-26217.html
 */
public class MarshalMapCVE202026217Test extends CamelTestSupport {

    @EndpointInject("mock:result")
    MockEndpoint mock;

    @Test
    @EnabledOnJre({ JRE.JAVA_11 })
    public void testMarshalListJDK11() {

        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("<?xml version='1.0' encoding='ISO-8859-1'?>"
                                    + "<map><entry><string>Test</string><string>21</string></entry></map>");

        HashMap<Object, Object> body = new HashMap<Object, Object>();
        body.put("Test", "21");

        Exception exception = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndProperty("direct:in", body, Exchange.CHARSET_NAME, "ISO-8859-1"));

        Assertions.assertInstanceOf(ForbiddenClassException.class, exception.getCause());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                XStreamDataFormat xStreamDataFormat = new XStreamDataFormat();

                from("direct:in").marshal(xStreamDataFormat).process(new Processor() {
                    @Override
                    public void process(Exchange exchange) {
                        exchange.getIn().setBody("<map>\n" +
                                                 "  <entry>\n" +
                                                 "    <jdk.nashorn.internal.objects.NativeString>\n" +
                                                 "      <flags>0</flags>\n" +
                                                 "      <value class='com.sun.xml.internal.bind.v2.runtime.unmarshaller.Base64Data'>\n"
                                                 +
                                                 "        <dataHandler>\n" +
                                                 "          <dataSource class='com.sun.xml.internal.ws.encoding.xml.XMLMessage$XmlDataSource'>\n"
                                                 +
                                                 "            <contentType>text/plain</contentType>\n" +
                                                 "            <is class='java.io.SequenceInputStream'>\n" +
                                                 "              <e class='javax.swing.MultiUIDefaults$MultiUIDefaultsEnumerator'>\n"
                                                 +
                                                 "                <iterator class='javax.imageio.spi.FilterIterator'>\n" +
                                                 "                  <iter class='java.util.ArrayList$Itr'>\n" +
                                                 "                    <cursor>0</cursor>\n" +
                                                 "                    <lastRet>-1</lastRet>\n" +
                                                 "                    <expectedModCount>1</expectedModCount>\n" +
                                                 "                    <outer-class>\n" +
                                                 "                      <java.lang.ProcessBuilder>\n" +
                                                 "                        <command>\n" +
                                                 "                          <string>calc</string>\n" +
                                                 "                        </command>\n" +
                                                 "                      </java.lang.ProcessBuilder>\n" +
                                                 "                    </outer-class>\n" +
                                                 "                  </iter>\n" +
                                                 "                  <filter class='javax.imageio.ImageIO$ContainsFilter'>\n" +
                                                 "                    <method>\n" +
                                                 "                      <class>java.lang.ProcessBuilder</class>\n" +
                                                 "                      <name>start</name>\n" +
                                                 "                      <parameter-types/>\n" +
                                                 "                    </method>\n" +
                                                 "                    <name>start</name>\n" +
                                                 "                  </filter>\n" +
                                                 "                  <next/>\n" +
                                                 "                </iterator>\n" +
                                                 "                <type>KEYS</type>\n" +
                                                 "              </e>\n" +
                                                 "              <in class='java.io.ByteArrayInputStream'>\n" +
                                                 "                <buf></buf>\n" +
                                                 "                <pos>0</pos>\n" +
                                                 "                <mark>0</mark>\n" +
                                                 "                <count>0</count>\n" +
                                                 "              </in>\n" +
                                                 "            </is>\n" +
                                                 "            <consumed>false</consumed>\n" +
                                                 "          </dataSource>\n" +
                                                 "          <transferFlavors/>\n" +
                                                 "        </dataHandler>\n" +
                                                 "        <dataLen>0</dataLen>\n" +
                                                 "      </value>\n" +
                                                 "    </jdk.nashorn.internal.objects.NativeString>\n" +
                                                 "    <string>test</string>\n" +
                                                 "  </entry>\n" +
                                                 "</map>");
                    }
                }).unmarshal(xStreamDataFormat).to(mock);
            }
        };
    }

}
