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

import org.apache.camel.Ordered;
import org.apache.camel.spi.PropertiesSource;

public class PromptPropertyPlaceholderSource implements PropertiesSource, Ordered {

    @Override
    public String getName() {
        return "prompt";
    }

    @Override
    public String getProperty(String name) {
        return null; // not in use
    }

    @Override
    public String getProperty(String name, String defaultValue) {
        String answer;
        if (defaultValue != null) {
            answer = System.console().readLine("Enter optional value for %s (%s): ", name, defaultValue);
        } else {
            do {
                answer = System.console().readLine("Enter required value for %s: ", name);
            } while (answer == null || answer.isBlank());
        }
        // if user press enter then the value should use the default value
        if (answer == null || answer.isBlank()) {
            answer = defaultValue;
        }
        return answer;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST;
    }
}
