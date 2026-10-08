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

/**
 * The error handling of one route, drawn in a frame below its happy path: lines saying where its failures go and where
 * it is reached on error from, then its onException clauses as blocks of their own.
 *
 * @param topY    the top of the frame, in layout units
 * @param bottomY the bottom of the frame, in layout units
 * @param lines   the lines at the top of the frame
 */
public record RouteErrorFrame(int topY, int bottomY, List<String> lines) {
}
