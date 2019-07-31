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
package org.apache.camel.component.zookeeper;

import java.util.Collections;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultMessage;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.data.Stat;

/**
 * <code>ZooKeeperMessage</code> is a {@link org.apache.camel.Message}
 * representing interactions with a ZooKeeper service. It contains a number of
 * optional Header Constants that are used by the Producer and consumer
 * mechanisms to finely control these interactions.
 */
public class ZooKeeperMessage extends DefaultMessage {

    public static final String ZOOKEEPER_NODE = "CamelZooKeeperNode";

    public static final String ZOOKEEPER_NODE_VERSION = "CamelZooKeeperVersion";

    public static final String ZOOKEEPER_ERROR_CODE = "CamelZooKeeperErrorCode";

    public static final String ZOOKEEPER_ACL = "CamelZookeeperAcl";

    public static final String ZOOKEEPER_CREATE_MODE = "CamelZookeeperCreateMode";

    public static final String ZOOKEEPER_STATISTICS = "CamelZookeeperStatistics";

    public static final String ZOOKEEPER_EVENT_TYPE = "CamelZookeeperEventType";

    public static final String ZOOKEEPER_OPERATION = "CamelZookeeperOperation";

    public ZooKeeperMessage(CamelContext camelContext) {
        super(camelContext);
    }

    public ZooKeeperMessage(CamelContext camelContext, String node, Stat statistics, WatchedEvent watchedEvent) {
        this(camelContext, node, statistics, Collections.<String, Object>emptyMap(), watchedEvent);
    }

    public ZooKeeperMessage(CamelContext camelContext, String node, Stat statistics, Map<String, Object> headers) {
        this(camelContext, node, statistics, headers, null);
    }

    public ZooKeeperMessage(CamelContext camelContext, String node, Stat statistics, Map<String, Object> headers, WatchedEvent watchedEvent) {
        super(camelContext);
        setHeaders(headers);
        this.setHeader(ZOOKEEPER_NODE, node);
        this.setHeader(ZOOKEEPER_STATISTICS, statistics);
        if (watchedEvent != null) {
            this.setHeader(ZOOKEEPER_EVENT_TYPE, watchedEvent.getType());
        }
    }

    public static Stat getStatistics(Message message) {
        Stat stats = null;
        if (message != null) {
            stats = message.getHeader(ZOOKEEPER_STATISTICS, Stat.class);
        }
        return stats;
    }

    public static String getPath(Message message) {
        String path = null;
        if (message != null) {
            path = message.getHeader(ZOOKEEPER_NODE, String.class);
        }
        return path;
    }

    @Override
    public DefaultMessage newInstance() {
        return new ZooKeeperMessage(getCamelContext());
    }
}
