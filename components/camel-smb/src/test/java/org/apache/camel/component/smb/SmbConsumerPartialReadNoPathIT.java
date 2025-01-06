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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultUuidGenerator;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SmbConsumerPartialReadNoPathIT extends SmbServerTestSupport {

    private final String uuid = new DefaultUuidGenerator().generateUuid() + ".txt";;

    @Override
    public void doPostSetup() throws Exception {
        template.sendBodyAndHeader(getSmbUrl(), "Hello Uuid", Exchange.FILE_NAME, uuid);
    }

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s?username=%s&password=%s&move=done&moveFailed=failed&searchPattern=%s",
                service.address(), service.shareName(), service.userName(), service.password(), uuid);
    }

    @Test
    public void testSmbSimpleConsumeNoPath() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Uuid");
        mock.expectedHeaderReceived(Exchange.FILE_NAME, uuid);

        MockEndpoint.assertIsSatisfied(context);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals("Hello Uuid",
                        new String(copyFileContentFromContainer("/data/rw/failed/" + uuid))));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getSmbUrl()).to("mock:result").throwException(new IllegalArgumentException("Forced"));
            }
        };
    }
}
