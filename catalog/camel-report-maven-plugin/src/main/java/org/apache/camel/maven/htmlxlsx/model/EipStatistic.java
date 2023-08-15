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
package org.apache.camel.maven.htmlxlsx.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class EipStatistic {

    protected String id;

    protected boolean tested;

    protected int totalProcessingTime;

    protected Properties properties = new Properties();

    protected Map<String, ChildEipStatistic> childEipStatisticMap = new LinkedHashMap<>();

    public String getId() {

        return id;
    }

    public void setId(String id) {

        this.id = id;
    }

    public boolean isTested() {

        return tested;
    }

    public void setTested(boolean tested) {

        this.tested = tested;
    }

    public int getTotalProcessingTime() {

        return totalProcessingTime;
    }

    public void setTotalProcessingTime(int totalProcessingTime) {

        this.totalProcessingTime = totalProcessingTime;
    }

    public Properties getProperties() {

        return properties;
    }

    public void setProperties(Properties properties) {

        this.properties = properties;
    }

    public Map<String, ChildEipStatistic> getChildEipStatisticMap() {

        return childEipStatisticMap;
    }

    public void setChildEipStatisticMap(Map<String, ChildEipStatistic> childEipStatisticMap) {

        this.childEipStatisticMap = childEipStatisticMap;
    }

    @Override
    public String toString() {

        return "EipStatistic{" +
               "id='" + id + '\'' +
               ", tested=" + tested +
               ", totalProcessingTime=" + totalProcessingTime +
               ", properties=" + properties +
               ", childEipStatisticMap=" + childEipStatisticMap +
               '}';
    }
}
