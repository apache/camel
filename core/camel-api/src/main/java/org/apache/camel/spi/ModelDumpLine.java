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

/**
 * Represents a line in a model dumper of the route structure (not with full details like a XML or YAML dump).
 *
 * @param location line source location:line (if present)
 * @param type     the kind of EIP node
 * @param id       the id of the EIP node
 * @param level    indent level of the EIP node
 * @param code     EIP code such as label or short name that is human-readable or pseudocode
 */
public record ModelDumpLine(String location, String type, String id, int level, String code) {
}
