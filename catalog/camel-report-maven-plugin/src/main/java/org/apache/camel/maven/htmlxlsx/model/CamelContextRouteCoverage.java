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

public class CamelContextRouteCoverage {

    private String id;

    private int exchangesTotal;

    private int totalProcessingTime;

    private Routes routes;

    public String getId() {

        return id;
    }

    public void setId(String id) {

        this.id = id;
    }

    public int getExchangesTotal() {

        return exchangesTotal;
    }

    public void setExchangesTotal(int exchangesTotal) {

        this.exchangesTotal = exchangesTotal;
    }

    public int getTotalProcessingTime() {

        return totalProcessingTime;
    }

    public void setTotalProcessingTime(int totalProcessingTime) {

        this.totalProcessingTime = totalProcessingTime;
    }

    public Routes getRoutes() {

        return routes;
    }

    public void setRoutes(Routes routes) {

        this.routes = routes;
    }

    @Override
    public String toString() {

        return "CamelContextRouteCoverage{" +
               "id='" + id + '\'' +
               ", exchangesTotal=" + exchangesTotal +
               ", totalProcessingTime=" + totalProcessingTime +
               ", routes=" + routes +
               '}';
    }
}
