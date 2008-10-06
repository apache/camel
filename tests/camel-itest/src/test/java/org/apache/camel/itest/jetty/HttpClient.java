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
package org.apache.camel.itest.jetty;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * An <code>HttpClient</code> is an example on how one would connect to an
 * HTTP endpoint. To use this <code>HttpClient</code>, you must:
 * <ol>
 * <li> specify the URL of the location URI of the HTTP endpoint you wish to
 * connect to, and </li>
 * <li> specify the xml file name in your classpath that you wish to send to the
 * endpoint</li>
 * </ol>
 */
public class HttpClient {
    private static String httpEndpoint = "http://localhost:8192/test";

    public static String send(String content) throws Exception, IOException {
        return send(new ByteArrayInputStream(content.getBytes()));
    }

    public static String send(InputStream inputStream) throws Exception, IOException {
        HttpURLConnection connection = getHttpConnection();
        connection.setDoOutput(true);
        OutputStream sender = connection.getOutputStream();
        byte[] inBuffer = new byte[256];
        for (int numBytesRead = inputStream.read(inBuffer); numBytesRead != -1; numBytesRead = inputStream
            .read(inBuffer)) {
            sender.write(inBuffer, 0, numBytesRead);
        }
        sender.close();
        inputStream.close();
        /*System.out.println("HTTP response code is: "
                 + connection.getResponseCode() + ". The status message is: "
                 + connection.getResponseMessage());*/

        // Read the response.
        InputStreamReader responseInputStream = new InputStreamReader(connection.getInputStream());
        BufferedReader receiver = new BufferedReader(responseInputStream);
        String response = "";
        String line;
        while ((line = receiver.readLine()) != null) {
            response += line;
        }
        response = response.replace("&lt;", "<");
        receiver.close();
        return response;
    }

    private static HttpURLConnection getHttpConnection() throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL(httpEndpoint).openConnection();
        return connection;
    }

    public void setHttpEndpoint(String endpointURI) {
        httpEndpoint = endpointURI;
    }
}