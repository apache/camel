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
package org.apache.camel.component.smb;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SmbProducerToDMoveExistingIT extends SmbServerTestSupport {

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s?username=%s&password=%s&path=${header.myDir}&fileExist=Move&moveExisting=old-${file:onlyname}",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Test
    public void testToDMoveExisting() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(2);

        Map<String, Object> headers = new HashMap<>();
        headers.put("myDir", "toDMoveExisting");
        headers.put(Exchange.FILE_NAME, "hello.txt");
        template.sendBodyAndHeaders("direct:start", "Hello World", headers);
        template.sendBodyAndHeaders("direct:start", "Bye World", headers);

        MockEndpoint.assertIsSatisfied(context);

        String data = service.smbFile("toDMoveExisting/old-hello.txt");
        Assertions.assertEquals("Hello World\n", data);
        data = service.smbFile("toDMoveExisting/hello.txt");
        Assertions.assertEquals("Bye World\n", data);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").toD(getSmbUrl()).to("mock:result");
            }
        };
    }

}
