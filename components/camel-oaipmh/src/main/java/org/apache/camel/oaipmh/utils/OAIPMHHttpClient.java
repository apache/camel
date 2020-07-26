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
package org.apache.camel.oaipmh.utils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OAIPMHHttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(OAIPMHHttpClient.class);

    public String doRequest(URI baseURI, String verb, String set, String from, String until, String metadataPrefix, String token, String identifier) throws IOException, URISyntaxException {
        CloseableHttpClient httpclient = getCloseableHttpClient();
        try {

            URIBuilder builder = new URIBuilder();
            builder.setScheme(baseURI.getScheme())
                    .setHost(baseURI.getHost())
                    .setPort(baseURI.getPort())
                    .setPath(baseURI.getPath())
                    .addParameter("verb", verb);

            if (baseURI.getQuery() != null && !baseURI.getQuery().isEmpty()) {
                for (String param : baseURI.getQuery().split("&")) {
                    builder.addParameter(param.split("=")[0], param.split("=")[1]);
                }
            }

            if (identifier != null) {
                builder.addParameter("identifier", identifier);
            }

            if (token != null) {
                builder.addParameter("resumptionToken", token);
            } else {
                builder.addParameter("metadataPrefix", metadataPrefix);
                if (set != null) {
                    builder.addParameter("set", set);
                }
                if (from != null) {
                    builder.addParameter("from", from);
                }
                if (until != null) {
                    builder.addParameter("until", until);
                }
            }

            HttpGet httpget = new HttpGet(builder.build());

            RequestLine requestLine = httpget.getRequestLine();

            LOG.info("Executing request: {} ", requestLine);

            // Create a custom response handler
            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {

                @Override
                public String handleResponse(final HttpResponse response) throws IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        if (entity == null) {
                            throw new IOException("No response received");
                        }
                        return EntityUtils.toString(entity, Charset.forName("UTF-8"));
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }

            };
            String responseBody = httpclient.execute(httpget, responseHandler);

           /* String uri = requestLine.getUri();
            System.out.println(uri);
            String sha256Hex = DigestUtils.sha256Hex(uri.split("edu.ec")[1]);
            System.out.println ("File:"+sha256Hex);
            BufferedWriter writer = new BufferedWriter(new FileWriter("/tmp/tests/test3/" + sha256Hex + ".xml"));
            writer.write(responseBody);
            writer.close();*/
            LOG.debug("Response received: {}", responseBody);
            
            return responseBody;
        } finally {
            httpclient.close();
        }
    }

    protected CloseableHttpClient getCloseableHttpClient() {
        return HttpClients.createDefault();
    }

}
