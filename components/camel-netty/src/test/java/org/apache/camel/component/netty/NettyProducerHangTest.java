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
package org.apache.camel.component.netty;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.assertStringContains;

public class NettyProducerHangTest extends CamelTestSupport {

    private static final int PORT = 4093;

    private static final Logger LOG = LoggerFactory.getLogger(NettyProducerHangTest.class);

    @Test
    public void nettyProducerHangsOnTheSecondRequestToTheSocketWhichIsClosed() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    acceptReplyAcceptClose();
                    acceptReplyAcceptClose();
                } catch (IOException e) {
                    LOG.error("Exception occured: {}", e.getMessage(), e);
                }
            }
        }).start();

        String response1
                = template.requestBody("netty:tcp://localhost:" + PORT + "?textline=true&sync=true", "request1", String.class);
        LOG.info("Received first response <{}>", response1);

        try {
            // our test server will close the socket now so we should get an error
            template.requestBody("netty:tcp://localhost:" + PORT + "?textline=true&sync=true", "request2", String.class);
        } catch (Exception e) {
            assertStringContains(e.getCause().getMessage(), "No response received from remote server");
        }

        String response2
                = template.requestBody("netty:tcp://localhost:" + PORT + "?textline=true&sync=true", "request3", String.class);
        LOG.info("Received 2nd response <{}>", response2);

        try {
            // our test server will close the socket now so we should get an error
            template.requestBody("netty:tcp://localhost:" + PORT + "?textline=true&sync=true", "request4", String.class);
        } catch (Exception e) {
            assertStringContains(e.getCause().getMessage(), "No response received from remote server");
        }
    }

    private void acceptReplyAcceptClose() throws IOException {
        byte buf[] = new byte[128];

        ServerSocket serverSocket = new ServerSocket(PORT);
        Socket soc = serverSocket.accept();

        LOG.info("Open socket and accept data");
        try (InputStream is = soc.getInputStream();
             OutputStream os = soc.getOutputStream()) {
            // read first message
            is.read(buf);

            // reply to the first message
            os.write("response\n".getBytes());

            // read second message
            is.read(buf);

            // do not reply, just close socket (emulate network problem)
        } finally {
            soc.close();
            serverSocket.close();
        }
        LOG.info("Close socket");
    }

}
