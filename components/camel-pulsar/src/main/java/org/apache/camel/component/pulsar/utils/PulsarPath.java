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
package org.apache.camel.component.pulsar.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PulsarPath {
    private static final Pattern PATTERN = Pattern.compile("^(persistent|non-persistent):?/?/(.+)/(.+)/(.+)$");

    private String persistence;
    private String tenant;
    private String namespace;
    private String topic;
    private boolean autoConfigurable;

    public PulsarPath(String path) {
        Matcher matcher = PATTERN.matcher(path);
        autoConfigurable = matcher.matches();
        if (autoConfigurable) {
            persistence = matcher.group(1);
            tenant = matcher.group(2);
            namespace = matcher.group(3);
            topic = matcher.group(4);
        }
    }

    public String getPersistence() {
        return persistence;
    }

    public String getTenant() {
        return tenant;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getTopic() {
        return topic;
    }

    public boolean isAutoConfigurable() {
        return autoConfigurable;
    }
}
