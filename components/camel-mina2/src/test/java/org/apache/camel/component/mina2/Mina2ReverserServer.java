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
package org.apache.camel.component.mina2;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

/**
 * (<b>Entry point</b>) Reverser server which reverses all text lines from
 * clients.
 */
public class Mina2ReverserServer {

    protected final int port;
    private IoAcceptor acceptor;

    public Mina2ReverserServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        acceptor = new NioSocketAcceptor();

        // Prepare the configuration
        ((NioSocketAcceptor) acceptor).setReuseAddress(true);
        Charset charset = Charset.forName("UTF-8");
        acceptor.getFilterChain().addLast("codec", new ProtocolCodecFilter(new TextLineCodecFactory(charset)));
        acceptor.setHandler(new Mina2ReverseProtocolHandler());

        // Bind
        acceptor.bind(new InetSocketAddress(port));
    }

    public void stop() throws Exception {
        acceptor.unbind();
    }

    public int getPort() {
        return port;
    }
}
