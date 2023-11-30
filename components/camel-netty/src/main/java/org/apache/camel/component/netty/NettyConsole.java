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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.Consumer;
import org.apache.camel.Route;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole("netty")
public class NettyConsole extends AbstractDevConsole {

    public NettyConsole() {
        super("camel", "netty", "Netty", "Embedded Netty Server");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        List<Consumer> list = getCamelContext().getRoutes()
                .stream().map(Route::getConsumer)
                .filter(c -> NettyConsumer.class.getName().equals(c.getClass().getName()))
                .collect(Collectors.toList());

        for (Consumer c : list) {
            NettyConsumer nc = (NettyConsumer) c;
            NettyConfiguration conf = nc.getConfiguration();
            // skip client mode as then it is not a local service
            if (!conf.isClientMode()) {
                sb.append(String.format("    %s:%s:%d\n", conf.getProtocol(), conf.getHost(), conf.getPort()));
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        List<Consumer> list = getCamelContext().getRoutes()
                .stream().map(Route::getConsumer)
                .filter(c -> NettyConsumer.class.getName().equals(c.getClass().getName()))
                .collect(Collectors.toList());

        List<JsonObject> arr = new ArrayList<>();
        for (Consumer c : list) {
            NettyConsumer nc = (NettyConsumer) c;
            NettyConfiguration conf = nc.getConfiguration();

            if (!conf.isClientMode()) {
                // skip client mode as then it is not a local service
                JsonObject jo = new JsonObject();
                jo.put("protocol", conf.getProtocol());
                jo.put("host", conf.getHost());
                jo.put("port", conf.getPort());
                arr.add(jo);
            }
        }
        if (!arr.isEmpty()) {
            root.put("consumers", arr);
        }

        return root;
    }

}
