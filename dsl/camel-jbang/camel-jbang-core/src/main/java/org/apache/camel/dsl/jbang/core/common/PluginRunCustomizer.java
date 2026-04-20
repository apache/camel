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
package org.apache.camel.dsl.jbang.core.common;

import java.util.List;

import org.apache.camel.main.KameletMain;

/**
 * Plugin hook that runs after the Run command has resolved file arguments and basic dependencies, but before plugin
 * exporter dependencies are added and {@link KameletMain#run()} builds the CamelContext.
 *
 * This allows plugins to customize the environment (system properties, config directories, initial properties) based on
 * the file arguments passed to the run command, so that plugin exporters can scan the right locations.
 */
public interface PluginRunCustomizer {

    /**
     * Called after the Run command has resolved file arguments and basic dependencies, but before plugin exporter
     * dependencies are added and KameletMain.run() builds the CamelContext.
     *
     * @param main  the KameletMain instance (for adding initial properties)
     * @param files the resolved file arguments passed to the run command (read-only)
     */
    void beforeRun(KameletMain main, List<String> files);
}
