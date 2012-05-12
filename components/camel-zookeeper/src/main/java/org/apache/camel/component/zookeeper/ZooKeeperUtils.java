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
package org.apache.camel.component.zookeeper;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.zookeeper.operations.WatchedEventProvider;
import org.apache.camel.component.zookeeper.operations.ZooKeeperOperation;
import org.apache.camel.util.ExchangeHelper;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.data.ACL;

/**
 * <code>ZooKeeperUtils</code> contains static utility functions mostly for
 * retrieving optional message properties from Message headers.
 */
public final class ZooKeeperUtils {

    private ZooKeeperUtils() { }
    
    /**
     * Pulls a createMode flag from the header keyed by
     * {@link ZooKeeperMessage#ZOOKEEPER_CREATE_MODE} in the given message and
     * attempts to parse a {@link CreateMode} from it.
     *
     * @param message the message that may contain a ZOOKEEPER_CREATE_MODE
     *            header.
     * @return the parsed {@link CreateMode} or null if the header was null or
     *         not a valid mode flag.
     */
    public static CreateMode getCreateMode(Message message, CreateMode defaultMode) {
        CreateMode mode = null;

        try {
            mode = message.getHeader(ZooKeeperMessage.ZOOKEEPER_CREATE_MODE, CreateMode.class);
        } catch (Exception e) {
        }
        
        if (mode == null) {
            String modeHeader = message.getHeader(ZooKeeperMessage.ZOOKEEPER_CREATE_MODE, String.class);
            mode = getCreateModeFromString(modeHeader, defaultMode);
        }
        return mode == null ? defaultMode : mode;
    }

    public static CreateMode getCreateModeFromString(String modeHeader, CreateMode defaultMode) {
        
        CreateMode mode = null;
        if (modeHeader != null) {
            try {
                mode = CreateMode.valueOf(modeHeader);
            } catch (Exception e) {
            }
        }
        return mode == null ? defaultMode : mode;
    }

    /**
     * Pulls the target node from the header keyed by
     * {@link ZooKeeperMessage#ZOOKEEPER_NODE}. This node is then typically used
     * in place of the configured node extracted from the endpoint uri.
     *
     * @param message the message that may contain a ZOOKEEPER_NODE header.
     * @return the node property or null if the header was null
     */
    public static String getNodeFromMessage(Message message, String defaultNode) {
        return getZookeeperProperty(message, ZooKeeperMessage.ZOOKEEPER_NODE, defaultNode, String.class);
    }

    public static Integer getVersionFromMessage(Message message) {
        return getZookeeperProperty(message, ZooKeeperMessage.ZOOKEEPER_NODE_VERSION, -1, Integer.class);
    }

    public static byte[] getPayloadFromExchange(Exchange exchange) {
        return ExchangeHelper.convertToType(exchange, byte[].class, exchange.getIn().getBody());
    }

    @SuppressWarnings("unchecked")
    public static List<ACL> getAclListFromMessage(Message in) {
        return getZookeeperProperty(in, ZooKeeperMessage.ZOOKEEPER_ACL, Ids.OPEN_ACL_UNSAFE, List.class);
    }

    public static <T> T getZookeeperProperty(Message m, String propertyName, T defaultValue, Class<? extends T> type) {
        T value = m.getHeader(propertyName, type);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }

    public static WatchedEvent getWatchedEvent(ZooKeeperOperation<?> zooKeeperOperation) {
        WatchedEvent watchedEvent = null;
        if (zooKeeperOperation instanceof WatchedEventProvider) {
            watchedEvent = ((WatchedEventProvider)zooKeeperOperation).getWatchedEvent();
        }
        return watchedEvent;
    }

    public static boolean hasWatchedEvent(ZooKeeperOperation<?> zooKeeperOperation) {
        return getWatchedEvent(zooKeeperOperation) != null;
    }
}
