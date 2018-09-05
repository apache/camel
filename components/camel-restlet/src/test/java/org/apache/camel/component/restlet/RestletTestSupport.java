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
package org.apache.camel.component.restlet;

import java.io.IOException;
import java.util.logging.Level;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.BeforeClass;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 *
 * @version 
 */
public abstract class RestletTestSupport extends CamelTestSupport {
    protected static int portNum;
    
    @BeforeClass
    public static void initializePortNum() {
        // restlet uses the JUL logger which is a pain to configure/install
        // we should not see WARN logs
        SLF4JBridgeHandler.install();
        java.util.logging.LogManager.getLogManager().getLogger("").setLevel(Level.INFO);

        portNum = AvailablePortFinder.getNextAvailable();
    }
    
    public HttpResponse doExecute(HttpUriRequest method) throws Exception {
        CloseableHttpClient client = HttpClientBuilder.create().build();
        try {
            HttpResponse response = client.execute(method);
            if (response.getEntity() != null) {
                response.setEntity(new BufferedHttpEntity(response.getEntity()));
            }
            return response;
        } finally {
            client.close();
        }
    }

    public static void assertHttpResponse(HttpResponse response, int expectedStatusCode,
                                          String expectedContentType) throws ParseException, IOException {
        assertHttpResponse(response, expectedStatusCode, expectedContentType, null);
    }

    public static void assertHttpResponse(HttpResponse response, int expectedStatusCode,
                                          String expectedContentType, String expectedBody)
        throws ParseException, IOException {
        assertEquals(expectedStatusCode, response.getStatusLine().getStatusCode());
        assertTrue(response.getFirstHeader("Content-Type").getValue().startsWith(expectedContentType));
        if (expectedBody != null) {
            assertEquals(expectedBody, EntityUtils.toString(response.getEntity()));
        }
    }
}
