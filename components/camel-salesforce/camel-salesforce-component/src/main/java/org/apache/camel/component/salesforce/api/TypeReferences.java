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
package org.apache.camel.component.salesforce.api;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.camel.component.salesforce.api.dto.Limits.Usage;
import org.apache.camel.component.salesforce.api.dto.RecentItem;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.api.dto.SearchResult;
import org.apache.camel.component.salesforce.api.dto.Version;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.RecentReport;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportInstance;

/**
 * Class that holds {@link TypeReference} instances needed for Jackson mapper to
 * support generics.
 */
public final class TypeReferences {

    public static final TypeReference<Map<String, Usage>> USAGES_TYPE = new TypeReference<Map<String, Usage>>() {
    };

    public static final TypeReference<List<RestError>> REST_ERROR_LIST_TYPE = new TypeReference<List<RestError>>() {
    };

    public static final TypeReference<List<ReportInstance>> REPORT_INSTANCE_LIST_TYPE = new TypeReference<List<ReportInstance>>() {
    };

    public static final TypeReference<List<RecentReport>> RECENT_REPORT_LIST_TYPE = new TypeReference<List<RecentReport>>() {
    };

    public static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<List<String>>() {
    };

    public static final TypeReference<List<Version>> VERSION_LIST_TYPE = new TypeReference<List<Version>>() {
    };

    public static final TypeReference<List<SearchResult>> SEARCH_RESULT_TYPE = new TypeReference<List<SearchResult>>() {
    };

    public static final TypeReference<List<RecentItem>> RECENT_ITEM_LIST_TYPE = new TypeReference<List<RecentItem>>() {
    };

    private TypeReferences() {
        // not meant for instantiation, only for TypeReference constants
    }

}
