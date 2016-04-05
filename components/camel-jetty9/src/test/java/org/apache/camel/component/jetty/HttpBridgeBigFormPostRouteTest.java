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
package org.apache.camel.component.jetty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;

public class HttpBridgeBigFormPostRouteTest extends BaseJettyTest {

    private static final String LARGE_HEADER_VALUE = "Lorem Ipsum is simply dummy text of the printing and typesetting industry. "
            + "Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley "
            + "of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap "
            + "into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of "
            + "Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus "
            + "PageMaker including versions of Lorem Ipsum. Lorem Ipsum is simply dummy text of the printing and typesetting "
            + "industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer "
            + "took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, "
            + "but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s "
            + "with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing "
            + "software like Aldus PageMaker including versions of Lorem Ipsum. Lorem Ipsum is simply dummy text of the printing "
            + "and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an "
            + "unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five "
            + "centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the "
            + "1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing "
            + "software like Aldus PageMaker including versions of Lorem Ipsum. Lorem Ipsum is simply dummy text of the printing and "
            + "typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown "
            + "printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, "
            + "but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the "
            + "release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus "
            + "PageMaker including versions of Lorem Ipsum." + "Lorem Ipsum is simply dummy text of the printing and typesetting industry. "
            + "Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley "
            + "of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap "
            + "into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of "
            + "Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus "
            + "PageMaker including versions of Lorem Ipsum. Lorem Ipsum is simply dummy text of the printing and typesetting "
            + "industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer "
            + "took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, "
            + "but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s "
            + "with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing "
            + "software like Aldus PageMaker including versions of Lorem Ipsum. Lorem Ipsum is simply dummy text of the printing "
            + "and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an "
            + "unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five "
            + "centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the "
            + "1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing "
            + "software like Aldus PageMaker including versions of Lorem Ipsum. Lorem Ipsum is simply dummy text of the printing and "
            + "typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown "
            + "printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, "
            + "but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the "
            + "release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus "
            + "PageMaker including versions of Lorem Ipsum.";

    private int port1;
    private int port2;

    @Test
    public void testHttpClient() throws Exception {
        
        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair("param1", LARGE_HEADER_VALUE));
        nvps.add(new BasicNameValuePair("param2", LARGE_HEADER_VALUE));
        nvps.add(new BasicNameValuePair("param3", LARGE_HEADER_VALUE));

        HttpEntity entity = new UrlEncodedFormEntity(nvps, Consts.UTF_8);
        HttpPost httpPost = new HttpPost("http://localhost:" + port2 + "/test/hello");
        httpPost.setEntity(entity);

        HttpHost proxy = new HttpHost("localhost", 8888, "http");
        RequestConfig config = RequestConfig.custom().setProxy(proxy).build();
        httpPost.setConfig(config);

        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            CloseableHttpResponse response = httpClient.execute(httpPost);
            assertEquals(response.getStatusLine().getStatusCode(), 200);
            response.close();
        } catch (IOException e) {
        } finally {
            httpClient.close();
        }
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                port1 = getPort();
                port2 = getNextPort();

                errorHandler(noErrorHandler());

                from("jetty:http://localhost:" + port2 + "/test/hello?matchOnUriPrefix=true")
                    .removeHeaders("formMetaData")
                    .to("http://localhost:" + port1 + "?bridgeEndpoint=true");
            }
        };
    }  

}
