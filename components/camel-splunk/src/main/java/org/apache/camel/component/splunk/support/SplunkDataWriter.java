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
package org.apache.camel.component.splunk.support;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import com.splunk.Args;
import com.splunk.Service;
import org.apache.camel.component.splunk.SplunkEndpoint;
import org.apache.camel.component.splunk.event.SplunkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SplunkDataWriter implements DataWriter {
    private static final Logger LOG = LoggerFactory.getLogger(SplunkDataWriter.class);

    protected SplunkEndpoint endpoint;
    protected Args args;
    private boolean connected;
    private Socket socket;

    public SplunkDataWriter(SplunkEndpoint endpoint, Args args) {
        this.endpoint = endpoint;
        this.args = args;
    }

    protected abstract Socket createSocket(Service service) throws IOException;

    @Override
    public void write(SplunkEvent event) throws Exception {
        doWrite(event.toString());
    }

    @Override
    public void write(String event) throws Exception {
        doWrite(event + SplunkEvent.LINEBREAK);
    }

    protected synchronized void doWrite(String event) throws IOException {
        LOG.debug("writing event to splunk:{}", event);
        OutputStream ostream = socket.getOutputStream();
        Writer writer = new OutputStreamWriter(ostream, StandardCharsets.UTF_8);
        writer.write(event);
        writer.flush();
    }

    @Override
    public synchronized void start() {
        try {
            socket = createSocket(endpoint.getService());
            connected = true;
        } catch (Exception e) {
            connected = false;
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void stop() {
        try {
            if (socket != null) {
                socket.close();
                connected = false;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }
}
