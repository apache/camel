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

package org.apache.camel.tooling.model;

import java.util.ArrayList;
import java.util.List;

public class JBangModel {

    protected final List<JBangGroupModel> groups = new ArrayList<>();
    protected final List<JBangOptionModel> options = new ArrayList<>();

    public List<JBangGroupModel> getGroups() {
        return groups;
    }

    public void addGroup(JBangGroupModel group) {
        groups.add(group);
    }

    public List<JBangOptionModel> getOptions() {
        return options;
    }

    public void addOption(JBangOptionModel option) {
        options.add(option);
    }

    public static class JBangGroupModel {

        private String name;
        private String description;
        private String sourceType;

        public JBangGroupModel() {}

        public JBangGroupModel(String name, String description, String sourceType) {
            this.name = name;
            this.description = description;
            this.sourceType = sourceType;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }
    }

    public static class JBangOptionModel extends BaseOptionModel {

        private String sourceType;

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }
    }
}
