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
package org.apache.camel.component.undertow;

import java.io.IOException;
import java.io.ObjectInputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.Assert;
import org.junit.Test;

public class UndertowTransferExceptionTest extends BaseUndertowTest {

    @Test
    public void getSerializedExceptionTest() throws IOException, ClassNotFoundException {
        HttpClient client = new HttpClient();
        GetMethod get = new GetMethod("http://localhost:" + getPort() + "/test/transfer");
        get.setRequestHeader("Accept", "application/x-java-serialized-object");
        client.executeMethod(get);
        ObjectInputStream in = new ObjectInputStream(get.getResponseBodyAsStream());
        IllegalArgumentException e = (IllegalArgumentException)in.readObject();
        Assert.assertNotNull(e);
        Assert.assertEquals(500, get.getStatusCode());
        Assert.assertEquals("Camel cannot do this", e.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {

                from("undertow:http://localhost:" + getPort() + "/test/transfer?transferException=true").to("mock:input")
                    .throwException(new IllegalArgumentException("Camel cannot do this"));
            }
        };
    }

}
