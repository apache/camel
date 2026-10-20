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
package org.apache.camel.spi;

import java.util.List;
import java.util.Map;

/**
 * The body types the nodes of the routes have been reached with, across all the exchanges.
 * <p/>
 * The message history records the type of the body as each node is reached
 * ({@link org.apache.camel.MessageHistory#getBodyType()}), but for one exchange at a time. This registry keeps, per
 * node, every distinct type the node has been reached with, so tooling can show what the body was at each step of a
 * route: also in a branch the last exchange did not take, and with all the types when they varied, such as after a
 * processor that sets a text for some messages and a Map for others.
 * <p/>
 * Each type also has its {@link Kind}. A node reached with bodies of more than one kind needs attention: a step that
 * writes or sends fails for the data, and a step that reads fields fails for the payload. Types of one kind, such as a
 * String and a byte[], are a part of the route that is dynamic, which the steps after it usually handle.
 * <p/>
 * It is fed when the message history is enabled (as the dev profile does), so it costs nothing otherwise. The body
 * types of a route are forgotten when the route is removed, so a route that is loaded again never mixes two versions.
 * The registry is a context plugin:
 * {@code camelContext.getCamelContextExtension().getContextPlugin(BodyTypeRegistry.class)}.
 *
 * @since 4.24
 */
public interface BodyTypeRegistry {

    /**
     * The kind of a body, for what a step can do with it.
     */
    enum Kind {

        /**
         * No body: it is null.
         */
        NULL,

        /**
         * The payload as it is: text, bytes, a stream or a file, or a single value such as a number, which converts to
         * text. What a step that writes or sends needs; a step that reads fields needs it unmarshalled first.
         */
        PAYLOAD,

        /**
         * Data: one Java object with fields to read, a Map or an object. What an unmarshal gives; a step that writes or
         * sends needs it marshalled first.
         */
        DATA,

        /**
         * A collection of objects: a List, a Set or an array, or an iterator or a stream over them (which can be read
         * once). A step that reads fields needs one element, such as by a split; a step that writes or sends needs it
         * marshalled first.
         */
        COLLECTION
    }

    /**
     * Records that a node was reached with a body of the given type.
     *
     * @param routeId  the id of the route the node belongs to
     * @param nodeId   the id of the node
     * @param bodyType the class name of the body, or {@code "null"} for a null body, as given by
     *                 {@link org.apache.camel.MessageHistory#getBodyType()}
     * @param kind     the kind of the body
     */
    void addBodyType(String routeId, String nodeId, String bodyType, Kind kind);

    /**
     * The body types the node has been reached with, in the order they were first seen. More than one means the type
     * varied between exchanges. A node keeps the first few distinct types only.
     *
     * @param  routeId the id of the route the node belongs to
     * @param  nodeId  the id of the node
     * @return         the body types, or an empty list when no exchange has reached the node since its route was added
     */
    List<String> getBodyTypes(String routeId, String nodeId);

    /**
     * The kinds of the bodies the node has been reached with, in the order they were first seen; all of them, also for
     * the types beyond the first few. More than one means the node needs attention.
     *
     * @param  routeId the id of the route the node belongs to
     * @param  nodeId  the id of the node
     * @return         the kinds, or an empty list when no exchange has reached the node since its route was added
     */
    List<Kind> getBodyKinds(String routeId, String nodeId);

    /**
     * The body types of every node of the route that an exchange has reached.
     *
     * @param  routeId the id of the route
     * @return         the body types by node id, or an empty map when no exchange has reached the route
     */
    Map<String, List<String>> getBodyTypes(String routeId);

    /**
     * Forgets the body types of the nodes of the route, such as when the route is removed.
     *
     * @param routeId the id of the route
     */
    void removeRoute(String routeId);

    /**
     * Forgets all the body types.
     */
    void clear();
}
