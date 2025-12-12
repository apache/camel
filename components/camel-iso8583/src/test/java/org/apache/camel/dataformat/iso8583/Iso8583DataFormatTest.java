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
package org.apache.camel.dataformat.iso8583;

import java.io.File;
import java.math.BigDecimal;

import com.solab.iso8583.IsoMessage;
import com.solab.iso8583.IsoValue;
import com.solab.iso8583.MessageFactory;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.solab.iso8583.IsoType.AMOUNT;

public class Iso8583DataFormatTest extends CamelTestSupport {

    @Test
    public void testUnmarshal() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").message(0).body().isInstanceOf(IsoMessage.class);

        template.sendBody("direct:unmarshal", new File("src/test/resources/parse1.txt"));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMarshal() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        MessageFactory mf = new MessageFactory();
        mf.setConfigPath("j8583-config.xml");

        IsoMessage iso
                = mf.parseMessage(context.getTypeConverter().convertTo(byte[].class, new File("src/test/resources/parse1.txt")),
                        "ISO015000055".getBytes().length);
        template.sendBody("direct:marshal", iso);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:unmarshal").unmarshal().iso8583("0210")
                        .process(e -> {
                            IsoMessage iso = (IsoMessage) e.getMessage().getBody();
                            Assertions.assertNotNull(iso);
                            IsoValue v = iso.getAt(4);
                            Assertions.assertNotNull(v);
                            Assertions.assertEquals(AMOUNT, v.getType());
                            Assertions.assertTrue(v.getValue() instanceof BigDecimal);
                            Assertions.assertEquals("30.00", v.getValue().toString());
                        })
                        .to("mock:result");

                from("direct:marshal").marshal().iso8583("0210").to("mock:result");
            }
        };
    }
}
