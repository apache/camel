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

public class ExampleModel {

    protected String fileName;
    protected String title;
    protected String category = "";
    protected String description = "";
    protected String readmeFileName = "readme.md";
    protected String deprecated;
    protected String middleFolder;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReadmeFileName() {
        return readmeFileName;
    }

    public void setReadmeFileName(String readmeFileName) {
        this.readmeFileName = readmeFileName;
    }

    public String getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(String deprecated) {
        this.deprecated = deprecated;
    }

    public String getMiddleFolder() {
        return middleFolder;
    }

    public void setMiddleFolder(String middleFolder) {
        this.middleFolder = middleFolder;
    }

    public String getDocLink() {
        if (middleFolder == null) {
            return fileName + "/" + readmeFileName;
        }
        return middleFolder + "/" + fileName + "/" + readmeFileName;
    }
}
