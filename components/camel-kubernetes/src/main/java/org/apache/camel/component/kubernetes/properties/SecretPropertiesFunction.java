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

import io.fabric8.kubernetes.api.model.Secret;
import org.apache.camel.spi.PropertiesFunction;

/**
 * A {@link PropertiesFunction} that can lookup from Kubernetes secret.
 */
@org.apache.camel.spi.annotations.PropertiesFunction("secret")
public class SecretPropertiesFunction extends BasePropertiesFunction {

    @Override
    public String getName() {
        return "secret";
    }

    @Override
    Path getMountPath() {
        if (getMountPathSecrets() != null) {
            return Paths.get(getMountPathSecrets());
        }
        return null;
    }

    @Override
    String lookup(String name, String key, String defaultValue) {
        String answer = null;
        Secret sec = getClient().secrets().withName(name).get();
        if (sec != null) {
            // string data can be used as-is
            answer = sec.getStringData() != null ? sec.getStringData().get(key) : null;
            if (answer == null) {
                // need to base64 decode from data
                answer = sec.getData() != null ? sec.getData().get(key) : null;
                if (answer != null) {
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
