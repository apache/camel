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
import java.net.Socket;

import com.splunk.Args;
import com.splunk.Index;
import com.splunk.Receiver;
import com.splunk.Service;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.splunk.SplunkEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamDataWriter extends SplunkDataWriter {
    private static final Logger LOG = LoggerFactory.getLogger(StreamDataWriter.class);

    private String index;

    public StreamDataWriter(SplunkEndpoint endpoint, Args args) {
        super(endpoint, args);
    }

    public void setIndex(String index) {
        this.index = index;
    }

    @Override
    protected Socket createSocket(Service service) throws IOException {
        Index indexObject = null;
        Receiver receiver = null;
        Socket socket = null;

        if (index != null) {
            indexObject = service.getIndexes().get(index);
            if (indexObject == null) {
                throw new RuntimeCamelException(String.format("cannot find index [%s]", index));
            }
            socket = indexObject.attach(args);
        } else {
            receiver = service.getReceiver();
            socket = receiver.attach(args);
        }
        socket.setTcpNoDelay(true);
        LOG.trace(String.format("created a socket on %s", socket.getRemoteSocketAddress()));
        return socket;
    }

}
