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
package org.apache.camel.component.kubernetes.properties;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import io.fabric8.kubernetes.api.model.ConfigMap;
import org.apache.camel.spi.PropertiesFunction;

/**
 * A {@link PropertiesFunction} that can lookup from Kubernetes configmaps.
 */
@org.apache.camel.spi.annotations.PropertiesFunction("configmap")
public class ConfigMapPropertiesFunction extends BasePropertiesFunction {

    @Override
    public String getName() {
        return "configmap";
    }

    @Override
    Path getMountPath() {
        if (getMountPathConfigMaps() != null) {
            return Paths.get(getMountPathConfigMaps());
        }
        return null;
    }

    @Override
    String lookup(String name, String key, String defaultValue) {
        String answer = null;
        ConfigMap cm = getClient().configMaps().withName(name).get();
        if (cm != null) {
            answer = cm.getData() != null ? cm.getData().get(key) : null;
            if (answer == null) {
                // maybe a binary data
                answer = cm.getBinaryData() != null ? cm.getBinaryData().get(key) : null;
                if (answer != null) {
                    // need to decode base64
                    byte[] data = Base64.getDecoder().decode(answer);
                    if (data != null) {
                        answer = new String(data);
                    }
                }
            }
        }
        if (answer == null) {
            return defaultValue;
        }

        return answer;
    }
}
