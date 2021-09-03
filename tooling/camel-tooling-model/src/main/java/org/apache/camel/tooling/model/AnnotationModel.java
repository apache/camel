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
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class AnnotationModel {

    protected String className;
    protected List<AnnotationOptionModel> options = new ArrayList<>();

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public List<AnnotationOptionModel> getOptions() {
        return options;
    }

    public void addOption(AnnotationOptionModel option) {
        options.add(option);
    }

    public List<AnnotationOptionModel> getSortedOptions() {
        return options.stream().sorted((o1, o2) -> {
            if (o1.isOptional() == o2.isOptional()) {
                return o1.getName().compareTo(o2.getName());
            } else {
                return Boolean.compare(o1.isOptional(), o2.isOptional());
            }
        }).collect(Collectors.toList());
    }

    public static class AnnotationOptionModel {

        protected String name;
        protected String type;
        protected String description;
        protected String defaultValue;
        protected boolean optional;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public boolean isOptional() {
            return optional;
        }

        public void setOptional(boolean optional) {
            this.optional = optional;
        }
    }
}
