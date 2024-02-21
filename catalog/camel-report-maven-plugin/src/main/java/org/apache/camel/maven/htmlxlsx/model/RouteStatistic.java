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

import java.util.List;
import java.util.Map;

public class RouteStatistic {

    private String id;

    private int totalEips;

    private int totalEipsTested;

    private int totalProcessingTime;

    private int coverage;

    private boolean totalEipsInitialized;

    private Map<Integer, List<EipStatistic>> eipStatisticMap;

    public String getId() {

        return id;
    }

    public void setId(String id) {

        this.id = id;
    }

    public int getTotalEips() {

        return totalEips;
    }

    public void setTotalEips(int totalEips) {

        this.totalEips = totalEips;
    }

    public int getTotalEipsTested() {

        return totalEipsTested;
    }

    public void setTotalEipsTested(int totalEipsTested) {

        this.totalEipsTested = totalEipsTested;
    }

    public int getTotalProcessingTime() {

        return totalProcessingTime;
    }

    public void setTotalProcessingTime(int totalProcessingTime) {

        this.totalProcessingTime = totalProcessingTime;
    }

    public int getCoverage() {

        return coverage;
    }

    public void setCoverage(int coverage) {

        this.coverage = coverage;
    }

    public boolean isTotalEipsInitialized() {

        return totalEipsInitialized;
    }

    public void setTotalEipsInitialized(boolean totalEipsInitialized) {

        this.totalEipsInitialized = totalEipsInitialized;
    }

    public Map<Integer, List<EipStatistic>> getEipStatisticMap() {

        return eipStatisticMap;
    }

    public void setEipStatisticMap(Map<Integer, List<EipStatistic>> eipStatisticMap) {

        this.eipStatisticMap = eipStatisticMap;
    }

    @Override
    public String toString() {

        return "RouteStatistic{" +
               "id='" + id + '\'' +
               ", totalEips=" + totalEips +
               ", totalEipsTested=" + totalEipsTested +
               ", totalProcessingTime=" + totalProcessingTime +
               ", coverage=" + coverage +
               ", totalEipsInitialized=" + totalEipsInitialized +
               ", eipStatisticMap=" + eipStatisticMap +
               '}';
    }
}
