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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.junit.Test;

public class UndertowPrefixMatchingTest extends BaseUndertowTest {

    @Test
    public void passOnExactPath() throws Exception {
        template.requestBody("http://localhost:{{port}}/myapp/suffix", "Hello Camel!");
        getMockEndpoint("mock:myapp").expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);
    }

    @Test
    public void failsOnPrefixPath() throws Exception {
        try {
            template.requestBody("http://localhost:{{port}}/myapp", "Hello Camel!");
            fail("Should fail, something is wrong");
        } catch (CamelExecutionException ex) {
            HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, ex.getCause());
            assertEquals(404, cause.getStatusCode());
        }
    }

    @Test
    public void passOnPrefixPath() throws Exception {
        template.requestBody("http://localhost:{{port}}/bar/somethingNotImportant", "Hello Camel!");
        getMockEndpoint("mock:myapp").expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("undertow:http://localhost:{{port}}/myapp/suffix")
                    .transform(bodyAs(String.class).append(" Must match exact path"))
                    .to("mock:myapp");

                from("undertow:http://localhost:{{port}}/bar?matchOnUriPrefix=true")
                    .transform(bodyAs(String.class).append(" Matching prefix"))
                    .to("mock:bar");
            }
        };
    }

}
