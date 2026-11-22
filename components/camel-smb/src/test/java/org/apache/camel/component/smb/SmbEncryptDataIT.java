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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A share that requires encryption (smb encrypt = required) refuses the connection unless encryptData=true.
 */
class SmbEncryptDataIT extends SmbServerTestSupport {

    private static final String ENCRYPTED_SHARE = "data-encrypted";

    @Test
    void encryptedShareRefusesWithoutEncryptData() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri(""), "Hello World", Exchange.FILE_NAME, "refused.txt"));

        GenericFileOperationFailedException cause = assertInstanceOf(GenericFileOperationFailedException.class, e.getCause());
        assertTrue(cause.getMessage().contains("STATUS_ACCESS_DENIED"), cause.getMessage());
    }

    @Test
    void encryptedShareWithEncryptData() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(uri("&encryptData=true"), "Hello World", Exchange.FILE_NAME, "hello.txt");

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(uri("&encryptData=true&delete=true&initialDelay=0&delay=200")).convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }

    private static String uri(String options) {
        return String.format("smb:%s/%s/encrypt-data?username=%s&password=%s%s", service.address(), ENCRYPTED_SHARE,
                service.username(), service.password(), options);
    }
}
