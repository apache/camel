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

package org.apache.camel.dsl.jbang.core.commands.plugin;

import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.util.json.JsonObject;

/**
 * Base command supports Kubernetes client related options such as namespace or custom kube config option. Automatically
 * applies the options to the Kubernetes client instance that is being used to run commands.
 */
abstract class PluginBaseCommand extends CamelCommand {

    public PluginBaseCommand(CamelJBangMain main) {
        super(main);
    }

    JsonObject loadConfig() {
        return PluginHelper.getOrCreatePluginConfig();
    }

    void saveConfig(JsonObject plugins) {
        PluginHelper.savePluginConfig(plugins);
    }
}
