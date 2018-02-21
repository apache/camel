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
package org.apache.camel.generator.swagger;

import java.util.Arrays;

import org.apache.camel.util.EndpointHelper;

class OperationFilter {

    // operation names to include separated by comma (wildcards can be used, eg find*)
    private String includes;

    public String getIncludes() {
        return includes;
    }

    public void setIncludes(String includes) {
        this.includes = includes;
    }

    boolean accept(String name) {
        boolean match = true;

        if (includes != null) {
            String[] patterns = includes.split(",");
            match = Arrays.stream(patterns).anyMatch(pattern -> EndpointHelper.matchPattern(name, pattern));
        }
        return match;
    }
}
