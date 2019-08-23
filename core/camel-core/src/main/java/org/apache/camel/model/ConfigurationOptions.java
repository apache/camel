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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

@Metadata(label = "configuration")
@XmlRootElement(name = "configuration")
@XmlAccessorType(XmlAccessType.FIELD)
public class ConfigurationOptions {
    @XmlElement(name = "option")
    public List<ConfigurationOption> globalOptions = new ArrayList<>();

    public Map<String, String> asMap() {
        return globalOptions.stream()
            .collect(Collectors.toMap(ConfigurationOption::name, ConfigurationOption::value));
    }

    public static ConfigurationOptions from(Map<String, String> map) {
        final ConfigurationOptions ret = new ConfigurationOptions();

        map.forEach((k, v) -> ret.globalOptions.add(new ConfigurationOption(k, v)));

        return ret;
    }

    public List<ConfigurationOption> getGlobalOptions() {
        return globalOptions;
    }

    public void setGlobalOptions(List<ConfigurationOption> globalOptions) {
        this.globalOptions = globalOptions;
    }
}
