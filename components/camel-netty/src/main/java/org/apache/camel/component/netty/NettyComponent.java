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

import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

public class NettyComponent extends DefaultComponent {
    // use a shared timer for Netty (see javadoc for HashedWheelTimer)
    private static volatile Timer timer;
    private NettyConfiguration configuration;

    public NettyComponent() {
    }

    public NettyComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        NettyConfiguration config;
        if (configuration != null) {
            config = configuration.copy();
        } else {
            config = new NettyConfiguration();
        }

        config.parseURI(new URI(remaining), parameters, this);

        NettyEndpoint nettyEndpoint = new NettyEndpoint(remaining, this, config);
        nettyEndpoint.setTimer(getTimer());
        setProperties(nettyEndpoint.getConfiguration(), parameters);
        return nettyEndpoint;
    }

    public NettyConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(NettyConfiguration configuration) {
        this.configuration = configuration;
    }

    public static Timer getTimer() {
        return timer;
    }

    @Override
    protected void doStart() throws Exception {
        if (timer == null) {
            timer = new HashedWheelTimer();
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        timer.stop();
        timer = null;
        super.doStop();
    }

}