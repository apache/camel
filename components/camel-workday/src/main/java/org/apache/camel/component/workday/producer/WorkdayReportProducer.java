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
package org.apache.camel.component.workday.producer;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.component.workday.WorkdayConfiguration;
import org.apache.camel.component.workday.WorkdayEndpoint;

/**
 * The Workday Report producer.
 */
public class WorkdayReportProducer extends WorkdayDefaultProducer {

    public static final String WORKDAY_RASS_URL_TEMPLATE = "https://%s/ccx/service/customreport2/%s%s";

    public WorkdayReportProducer(WorkdayEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public String prepareUri(WorkdayConfiguration configuration) {
        Map<String, Object> parameters = configuration.getParameters();
        StringBuilder stringBuilder = new StringBuilder(configuration.getPath());
        stringBuilder.append("?");
        if (parameters.size() > 0) {
            String params = parameters.keySet().stream().map(k -> k + "=" + parameters.get(k)).collect(Collectors.joining("&"));
            stringBuilder.append(params);
            stringBuilder.append("&");
        }

        stringBuilder.append("format=");
        stringBuilder.append(configuration.getReportFormat());

        return String.format(WORKDAY_RASS_URL_TEMPLATE, configuration.getHost(), configuration.getTenant(),
                stringBuilder.toString());
    }

}
