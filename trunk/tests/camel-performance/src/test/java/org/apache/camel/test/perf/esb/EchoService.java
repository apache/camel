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
package org.apache.camel.test.perf.esb;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class EchoService extends HttpServlet {

    public static volatile long delayMillis;

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_BUFFER_SIZE = 4;

    public void doPost(HttpServletRequest request, HttpServletResponse response) {
        ByteBuffer bb;
        List<ByteBuffer> bbList = null;

        try {
            int bufKBytes = DEFAULT_BUFFER_SIZE;
            int delaySecs = 0;

            String soapAction = request.getHeader("SOAPAction");
            if (soapAction != null) {
                if (soapAction.startsWith("\"")) {
                    soapAction = soapAction.replaceAll("\"", "");
                }
                int dotPos = soapAction.indexOf(".");
                int secondDotPos = dotPos == -1 ? -1 : soapAction.indexOf(".", dotPos + 1);

                if (secondDotPos > 0) {
                    bufKBytes = Integer.parseInt(soapAction.substring(dotPos + 1, secondDotPos));
                    delaySecs = Integer.parseInt(soapAction.substring(secondDotPos + 1));
                } else if (dotPos > 0) {
                    bufKBytes = Integer.parseInt(soapAction.substring(dotPos + 1));
                }
            }

            bb = ByteBuffer.allocate(bufKBytes * 1024);
            ReadableByteChannel rbc = Channels.newChannel(request.getInputStream());

            int len = 0;
            int tot = 0;
            while ((len = rbc.read(bb)) > 0) {
                tot += len;
                if (tot >= bb.capacity()) {
                    // --- auto expand logic ---
                    if (bbList == null) {
                        bbList = new ArrayList<ByteBuffer>();
                    }
                    bb.flip();
                    bbList.add(bb);
                    bufKBytes = bufKBytes * 2;
                    bb = ByteBuffer.allocate(bufKBytes * 1024);
                }
            }
            bb.flip();

            // sleep when a "sleep" header exists - but if "port" is also specified, only when it matches
            String sleep = request.getHeader("sleep");
            if (sleep != null) {
                String port = request.getHeader("port");
                if (port != null) {
                    if (request.getLocalPort() == Integer.parseInt(port)) {
                        Long sleepVal = Long.parseLong(sleep);
                        System.out.println("Echo Service on port : " + port + " sleeping for : " + sleepVal);
                        Thread.sleep(sleepVal);
                    }
                } else {
                    Long sleepVal = Long.parseLong(sleep);
                    System.out.println("Echo Service on port : " + request.getLocalPort() + " sleeping for : " + sleepVal);
                    Thread.sleep(sleepVal);
                }
            }

            // --- auto expand logic ---
            if (bbList != null) {
                bbList.add(bb);
            }

            if (delaySecs > 0 && request.getLocalPort() == 9000) { // sleep only when running on port 9000
                Thread.sleep(delaySecs * 1000);
            } else if (delayMillis > 0) {
                Thread.sleep(delayMillis);
            }

            response.setContentType(request.getContentType());
            response.setHeader("port", Integer.toString(request.getLocalPort()));
            //System.out.println("Reply from Echo service on port : " + request.getLocalPort());

            OutputStream out = response.getOutputStream();
            WritableByteChannel wbc = Channels.newChannel(out);

            if (bbList == null) {
                do {
                    len = wbc.write(bb);
                } while (len > 0);

            } else {
                // --- auto expand logic ---
                for (ByteBuffer b : bbList) {
                    do {
                        len = wbc.write(b);
                    } while (len > 0);
                }
            }
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
