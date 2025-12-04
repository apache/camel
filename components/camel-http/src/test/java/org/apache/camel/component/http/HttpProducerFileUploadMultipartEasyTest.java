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

package org.apache.camel.component.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.util.StringHelper;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

public class HttpProducerFileUploadMultipartEasyTest extends BaseHttpTest {

    private HttpServer localServer;

    private String endpointUrl;

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy())
                .setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/upload3", (request, response, context) -> {

                    // we receive FromData so we need to parse the multipart to grab the information
                    var e = request.getEntity();
                    var s = new String(e.getContent().readAllBytes());
                    String cd = StringHelper.between(s, "Content-Disposition:", "\n");
                    var n = StringHelper.removeQuotes(StringHelper.after(cd, "filename="))
                            .trim();
                    var n2 = StringHelper.removeQuotes(StringHelper.between(cd, "name=", ";"))
                            .trim();
                    response.setEntity(new StringEntity(n2 + ";" + n));

                    response.setCode(HttpStatus.SC_OK);
                })
                .create();
        localServer.start();

        endpointUrl = "http://localhost:" + localServer.getLocalPort();
    }

    @Override
    public void cleanupResources() throws Exception {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void testFileUpload() {
        File f = new File("src/test/resources/log4j2.properties");

        Exchange out =
                template.request(endpointUrl + "/upload3?multipartUpload=true&multipartUploadName=cheese", exchange -> {
                    exchange.getMessage().setBody(f);
                });

        assertNotNull(out);
        assertFalse(out.isFailed(), "Should not fail");
        assertEquals("cheese;log4j2.properties", out.getMessage().getBody(String.class));
    }
}
