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
package org.apache.camel.component.mina;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.converter.ObjectConverter;
import org.apache.camel.impl.DefaultComponent;
import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.support.BaseIoConnectorConfig;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.apache.mina.filter.codec.serialization.ObjectSerializationCodecFactory;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.DatagramAcceptor;
import org.apache.mina.transport.socket.nio.DatagramConnector;
import org.apache.mina.transport.socket.nio.DatagramConnectorConfig;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.apache.mina.transport.socket.nio.SocketConnector;
import org.apache.mina.transport.socket.nio.SocketConnectorConfig;
import org.apache.mina.transport.vmpipe.VmPipeAcceptor;
import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.mina.transport.vmpipe.VmPipeConnector;

/**
 * @version $Revision$
 */
public class MinaComponent extends DefaultComponent<MinaExchange> {
    private CharsetEncoder encoder;

    public MinaComponent() {
    }

    public MinaComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint<MinaExchange> createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        URI u = new URI(remaining);

        String protocol = u.getScheme();
        if (protocol.equals("tcp")) {
            return createSocketEndpoint(uri, u, parameters);
        }
        else if (protocol.equals("udp") || protocol.equals("mcast") || protocol.equals("multicast")) {
            return createDatagramEndpoint(uri, u, parameters);
        }
        else if (protocol.equals("vm")) {
            return createVmEndpoint(uri, u);
        }
        else {
            throw new IOException("Unrecognised MINA protocol: " + protocol + " for uri: " + uri);
        }
    }

    protected MinaEndpoint createVmEndpoint(String uri, URI connectUri) {
        IoAcceptor acceptor = new VmPipeAcceptor();
        SocketAddress address = new VmPipeAddress(connectUri.getPort());
        IoConnector connector = new VmPipeConnector();
        return new MinaEndpoint(uri, this, address, acceptor, connector, null);
    }

    protected MinaEndpoint createSocketEndpoint(String uri, URI connectUri, Map parameters) {
        IoAcceptor acceptor = new SocketAcceptor();
        SocketAddress address = new InetSocketAddress(connectUri.getHost(), connectUri.getPort());
        IoConnector connector = new SocketConnector();

        // TODO customize the config via URI
        SocketConnectorConfig config = new SocketConnectorConfig();
        configureSocketCodecFactory(config, parameters);
        return new MinaEndpoint(uri, this, address, acceptor, connector, config);
    }

    protected void configureSocketCodecFactory(BaseIoConnectorConfig config, Map parameters) {
        ProtocolCodecFactory codecFactory = getCodecFactory(parameters);

        boolean textline = false;
        if (codecFactory == null) {
            if (parameters != null) {
                textline = ObjectConverter.toBool(parameters.get("textline"));
            }
            if (textline) {
                codecFactory = new TextLineCodecFactory();
            }
            else {
                codecFactory = new ObjectSerializationCodecFactory();
            }
        }
        addCodecFactory(config, codecFactory);
    }

    protected MinaEndpoint createDatagramEndpoint(String uri, URI connectUri, Map parameters) {
        IoAcceptor acceptor = new DatagramAcceptor();
        SocketAddress address = new InetSocketAddress(connectUri.getHost(), connectUri.getPort());
        IoConnector connector = new DatagramConnector();

        // TODO customize the config via URI
        DatagramConnectorConfig config = new DatagramConnectorConfig();

        configureDataGramCodecFactory(config, parameters);

        return new MinaEndpoint(uri, this, address, acceptor, connector, config);
    }

    /**
     * For datagrams the entire message is available as a single ByteBuffer so lets just pass those around by default
     * and try converting whatever they payload is into ByteBuffers unless some custom converter is specified
     */
    protected void configureDataGramCodecFactory(BaseIoConnectorConfig config, Map parameters) {
        ProtocolCodecFactory codecFactory = getCodecFactory(parameters);
        if (codecFactory == null) {
            codecFactory = new ProtocolCodecFactory() {
                public ProtocolEncoder getEncoder() throws Exception {
                    return new ProtocolEncoder() {
                        public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
                            ByteBuffer buf = toByteBuffer(message);
                            buf.flip();
                            out.write(buf);
                        }

                        public void dispose(IoSession session) throws Exception {
                        }
                    };
                }

                public ProtocolDecoder getDecoder() throws Exception {
                    return new ProtocolDecoder() {
                        public void decode(IoSession session, ByteBuffer in, ProtocolDecoderOutput out) throws Exception {
                            // lets just pass the ByteBuffer in
                            out.write(in);
                        }

                        public void finishDecode(IoSession session, ProtocolDecoderOutput out) throws Exception {
                        }

                        public void dispose(IoSession session) throws Exception {
                        }
                    };
                }
            };
        }
        addCodecFactory(config, codecFactory);
        //addCodecFactory(config, new TextLineCodecFactory());
    }

    protected ByteBuffer toByteBuffer(Object message) throws CharacterCodingException {
        ByteBuffer answer = convertTo(ByteBuffer.class, message);
        if (answer == null && message instanceof byte[]) {
            answer = MinaConverter.toByteBuffer((byte[]) message);
        }
        if (answer == null) {
            String value = convertTo(String.class, message);
            answer = ByteBuffer.allocate(value.length()).setAutoExpand(true);

            if (value != null) {
                if (encoder == null) {
                    encoder = Charset.defaultCharset().newEncoder();
                }
                answer.putString(value, encoder);
            }
        }
        return answer;
    }

    protected ProtocolCodecFactory getCodecFactory(Map parameters) {
        ProtocolCodecFactory codecFactory = null;
        if (parameters != null) {
            String codec = (String) parameters.get("codec");
            if (codec != null) {
                codecFactory = getCamelContext().getRegistry().lookup(codec, ProtocolCodecFactory.class);
            }
        }
        return codecFactory;
    }

    protected void addCodecFactory(BaseIoConnectorConfig config, ProtocolCodecFactory codecFactory) {
        config.getFilterChain().addLast("codec", new ProtocolCodecFilter(codecFactory));
    }
}
