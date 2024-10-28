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
package org.apache.camel.support.startup;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.StartupCondition;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Startup condition that waits for an ENV variable to exists.
 */
public class EnvStartupCondition implements StartupCondition {

    private final String env;

    public EnvStartupCondition(String env) {
        ObjectHelper.notNullOrEmpty(env, "ENV");
        this.env = env;
    }

    @Override
    public String getName() {
        return "ENV";
    }

    @Override
    public String getWaitMessage() {
        return "Waiting for OS Environment Variable: " + env;
    }

    @Override
    public String getFailureMessage() {
        return "OS Environment Variable: " + env + " does not exist";
    }

    protected String lookupEnvironmentVariable(String env) {
        return IOHelper.lookupEnvironmentVariable(env);
    }

    @Override
    public boolean canContinue(CamelContext camelContext) throws Exception {
        String value = lookupEnvironmentVariable(env);
        return ObjectHelper.isNotEmpty(value);
    }

}
