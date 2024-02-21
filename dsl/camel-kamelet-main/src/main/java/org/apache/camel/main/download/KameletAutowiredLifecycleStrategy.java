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
package org.apache.camel.main.download;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.main.MainAutowiredLifecycleStrategy;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Language;
import org.apache.camel.support.PatternHelper;

public class KameletAutowiredLifecycleStrategy extends MainAutowiredLifecycleStrategy {

    private final String stubPattern;
    private final boolean silent;

    public KameletAutowiredLifecycleStrategy(CamelContext camelContext, String stubPattern, boolean silent) {
        super(camelContext);
        this.stubPattern = stubPattern;
        this.silent = silent;
    }

    @Override
    protected boolean isEnabled(String name, Component component) {
        boolean enabled = isEnabled(name);
        if (enabled) {
            return super.isEnabled(name, component);
        } else {
            return false;
        }
    }

    @Override
    protected boolean isEnabled(String name, Language language) {
        boolean enabled = isEnabled(name);
        if (enabled) {
            return super.isEnabled(name, language);
        } else {
            return false;
        }
    }

    @Override
    protected boolean isEnabled(String name, DataFormat dataFormat) {
        boolean enabled = isEnabled(name);
        if (enabled) {
            return super.isEnabled(name, dataFormat);
        } else {
            return false;
        }
    }

    protected boolean isEnabled(String name) {
        if (silent) {
            return false;
        }
        if (stubPattern == null) {
            return true;
        } else if (stubPattern.equals("*")) {
            return false;
        } else {
            // is the component stubbed, then it should not autowire
            for (String n : stubPattern.split(",")) {
                if (PatternHelper.matchPattern(n, stubPattern)) {
                    return false;
                }
            }
        }
        return true;
    }
}
