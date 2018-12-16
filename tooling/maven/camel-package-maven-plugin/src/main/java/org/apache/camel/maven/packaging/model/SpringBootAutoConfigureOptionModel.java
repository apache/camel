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
package org.apache.camel.maven.packaging.model;

import org.apache.camel.maven.packaging.StringHelper;

import static org.apache.camel.maven.packaging.StringHelper.wrapCamelCaseWords;

public class SpringBootAutoConfigureOptionModel {

    private String name;
    private String javaType;
    private String defaultValue;
    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJavaType() {
        return javaType;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getShortJavaType() {
        return getShortJavaType(40);
    }

    public String getShortJavaType(int watermark) {

        String text = StringHelper.getClassShortName(javaType);

        // if its some kind of custom java object then lets wrap it as its long
        if (!javaType.startsWith("java.")) {
            text = wrapCamelCaseWords(text, watermark, " ");
        }

        return text;
    }

    public String getShortDefaultValue(int watermark) {
        if (defaultValue == null || defaultValue.isEmpty()) {
            return "";
        }
        String text = defaultValue;
        if (text.endsWith("<T>")) {
            text = text.substring(0, text.length() - 3);
        } else if (text.endsWith("<T>>")) {
            text = text.substring(0, text.length() - 4);
        }

        // TODO: dirty hack for AUTO_ACKNOWLEDGE which we should wrap
        if ("AUTO_ACKNOWLEDGE".equals(text)) {
            return "AUTO_ ACKNOWLEDGE";
        }

        return text;
    }


}
