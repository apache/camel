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
package org.apache.camel.component.knative.spi;

import org.apache.camel.spi.Configurer;

@Configurer
public class KnativeSinkBinding {
    private String name;
    private Knative.Type type;
    private String objectKind;
    private String objectApiVersion;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Knative.Type getType() {
        return type;
    }

    public void setType(Knative.Type type) {
        this.type = type;
    }

    public String getObjectKind() {
        return objectKind;
    }

    public void setObjectKind(String objectKind) {
        this.objectKind = objectKind;
    }

    public String getObjectApiVersion() {
        return objectApiVersion;
    }

    public void setObjectApiVersion(String objectApiVersion) {
        this.objectApiVersion = objectApiVersion;
    }

    @Override
    public String toString() {
        return "KnativeSinkBinding{" +
               "name='" + name + '\'' +
               ", type=" + type +
               ", objectKind='" + objectKind + '\'' +
               ", objectApiVersion='" + objectApiVersion + '\'' +
               '}';
    }
}
