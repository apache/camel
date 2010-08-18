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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;

public abstract class ClientPipelineFactory implements ChannelPipelineFactory {
    protected NettyProducer producer;
    protected Exchange exchange;
    protected AsyncCallback callback;

    public ClientPipelineFactory() {
    }
    
    public ClientPipelineFactory(NettyProducer producer, Exchange exchange, AsyncCallback callback) {
        this.producer = producer;
        this.exchange = exchange;
        this.callback = callback;
    }
    
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline channelPipeline = Channels.pipeline();
        return channelPipeline;
    }

    public NettyProducer getProducer() {
        return producer;
    }

    public void setProducer(NettyProducer producer) {
        this.producer = producer;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    public AsyncCallback getCallback() {
        return callback;
    }

    public void setCallback(AsyncCallback callback) {
        this.callback = callback;
    }

}
