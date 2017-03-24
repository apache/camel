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
package org.apache.camel.spi;

import java.util.List;

import org.apache.camel.ComponentConfiguration;

/**
 * A helper interface used by the {@link org.apache.camel.ComponentConfiguration#completeEndpointPath(String)} method
 * to allow endpoint paths to be completed.
 *
 * {@link org.apache.camel.Component} implementations should try to implement this API to make your component
 * behave nicer in command line, IDE and web based tools.
 */
@Deprecated
public interface EndpointCompleter {

    /**
     * Given the configuration and completion text, return a list of possible completion values
     * for a command line, IDE or web based tool.
     *
     * @return the list of completion values if any (rather like bash completion, prefix values can be returned
     * - such as just the directories in the current path rather than returning every possible file name on a disk).
     */
    List<String> completeEndpointPath(ComponentConfiguration configuration, String completionText);
}
