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
package org.apache.camel.dsl.jbang.core.commands.tui.diagram;

import java.util.List;
import java.util.Map;

/**
 * The counts a topology whose boxes are not the running routes (the architecture) moves its dots at: the readings by
 * their key, and for each edge (by the ids of its boxes, {@code from>to}) the reading it moves at.
 *
 * @param readings the counts by their key
 * @param edges    the reading of each edge, by {@link #edgeId}
 */
public record FlowFeed(Map<String, FlowAnimation.Reading> readings, Map<String, Edge> edges) {

    /**
     * The reading an edge moves at, and the readings of the routes that send along it: none of them moving, the edge
     * does not either (none means it always may).
     */
    public record Edge(String key, List<String> senders) {
    }

    public static String edgeId(String from, String to) {
        return from + ">" + to;
    }
}
