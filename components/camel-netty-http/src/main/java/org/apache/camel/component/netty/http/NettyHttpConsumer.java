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
package org.apache.camel.component.netty.http;

import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.component.netty.NettyConfiguration;
import org.apache.camel.component.netty.NettyConsumer;
import org.apache.camel.util.ObjectHelper;

/**
 * HTTP based {@link NettyConsumer}
 */
public class NettyHttpConsumer extends NettyConsumer implements Suspendable {

    public NettyHttpConsumer(NettyHttpEndpoint nettyEndpoint, Processor processor, NettyConfiguration configuration) {
        super(nettyEndpoint, processor, configuration);
    }

    @Override
    public NettyHttpEndpoint getEndpoint() {
        return (NettyHttpEndpoint) super.getEndpoint();
    }

    @Override
    public NettyHttpConfiguration getConfiguration() {
        return (NettyHttpConfiguration) super.getConfiguration();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ObjectHelper.notNull(getNettyServerBootstrapFactory(), "HttpServerBootstrapFactory", this);
        getNettyServerBootstrapFactory().addConsumer(this);
    }

    @Override
    protected void doStop() throws Exception {
        getNettyServerBootstrapFactory().removeConsumer(this);
        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        if (getConfiguration().isSend503whenSuspended()) {
            // noop as the server handler will send back 503 when suspended
        } else {
            // will stop the acceptor
            doStop();
        }
    }

    @Override
    protected void doResume() throws Exception {
        if (getConfiguration().isSend503whenSuspended()) {
            // noop
        } else {
            // will start the acceptor
            doStart();
        }
    }
}
