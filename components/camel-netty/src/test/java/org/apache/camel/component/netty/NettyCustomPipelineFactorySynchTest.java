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
package org.apache.camel.component.netty;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty.handlers.ClientChannelHandler;
import org.apache.camel.component.netty.handlers.ServerChannelHandler;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.CamelTestSupport;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyCustomPipelineFactorySynchTest extends CamelTestSupport {
    private static final transient Logger LOG = LoggerFactory.getLogger(NettyCustomPipelineFactorySynchTest.class);

    @Produce(uri = "direct:start")
    protected ProducerTemplate producerTemplate;
    private TestClientChannelPipelineFactory clientPipelineFactory;
    private TestServerChannelPipelineFactory serverPipelineFactory;
    private String response;
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = new JndiRegistry(createJndiContext());
        clientPipelineFactory = new TestClientChannelPipelineFactory();
        serverPipelineFactory = new TestServerChannelPipelineFactory();
        registry.bind("cpf", clientPipelineFactory);
        registry.bind("spf", serverPipelineFactory);
        return registry;
    }
    
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private void sendRequest() throws Exception {
        // Async request
        response = (String) producerTemplate.requestBody(
            "netty:tcp://localhost:5110?clientPipelineFactory=#cpf&sync=true&textline=true", 
            "Forest Gump describing Vietnam...");        
    }
    
    @Test
    public void testCustomClientPipelineFactory() throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("netty:tcp://localhost:5110?serverPipelineFactory=#spf&sync=true&textline=true")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            exchange.getOut().setBody("Forrest Gump: We was always taking long walks, and we was always looking for a guy named 'Charlie'");                           
                        }
                    });                
            }
        });
        context.start();
        
        LOG.debug("Beginning Test ---> testCustomClientPipelineFactory()");       
        sendRequest();
        LOG.debug("Completed Test ---> testCustomClientPipelineFactory()");
        context.stop();
        
        assertEquals("Forrest Gump: We was always taking long walks, and we was always looking for a guy named 'Charlie'", response);
        assertEquals(true, clientPipelineFactory.isfactoryInvoked());
        assertEquals(true, serverPipelineFactory.isfactoryInvoked());
    } 
    
    public class TestClientChannelPipelineFactory extends ClientPipelineFactory {
        private int maxLineSize = 1024;
        private boolean invoked;
        
        public ChannelPipeline getPipeline() throws Exception {
            invoked = true;
            
            ChannelPipeline channelPipeline = Channels.pipeline();

            channelPipeline.addLast("decoder-DELIM", new DelimiterBasedFrameDecoder(maxLineSize, true, Delimiters.lineDelimiter()));
            channelPipeline.addLast("decoder-SD", new StringDecoder(CharsetUtil.UTF_8));
            channelPipeline.addLast("encoder-SD", new StringEncoder(CharsetUtil.UTF_8));            
            channelPipeline.addLast("handler", new ClientChannelHandler(producer, exchange, callback));

            return channelPipeline;
        }
        
        public boolean isfactoryInvoked() {
            return invoked;
        }
    }
    
    public class TestServerChannelPipelineFactory extends ServerPipelineFactory {
        private int maxLineSize = 1024;
        private boolean invoked;
        
        public ChannelPipeline getPipeline() throws Exception {
            invoked = true;
            
            ChannelPipeline channelPipeline = Channels.pipeline();

            channelPipeline.addLast("encoder-SD", new StringEncoder(CharsetUtil.UTF_8));
            channelPipeline.addLast("decoder-DELIM", new DelimiterBasedFrameDecoder(maxLineSize, true, Delimiters.lineDelimiter()));
            channelPipeline.addLast("decoder-SD", new StringDecoder(CharsetUtil.UTF_8));
            channelPipeline.addLast("handler", new ServerChannelHandler(consumer));

            return channelPipeline;
        }
        
        public boolean isfactoryInvoked() {
            return invoked;
        }
    }

}

