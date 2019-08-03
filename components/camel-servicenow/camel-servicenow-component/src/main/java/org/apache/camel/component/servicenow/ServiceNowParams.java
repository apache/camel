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
package org.apache.camel.component.servicenow;

import java.util.function.Function;

import org.apache.camel.Message;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

public enum ServiceNowParams implements ServiceNowParam {
    PARAM_SYS_ID("sys_id", "CamelServiceNowSysId", String.class),
    PARAM_USER_SYS_ID("user_sysid", "CamelServiceNowUserSysId", String.class),
    PARAM_USER_ID("user_id", "CamelServiceNowUserId", String.class),
    PARAM_CART_ITEM_ID("cart_item_id", "CamelServiceNowCartItemId", String.class),
    PARAM_FILE_NAME("file_name", "CamelServiceNowFileName", String.class),
    PARAM_TABLE_NAME("table_name", "CamelServiceNowTable", String.class),
    PARAM_TABLE_SYS_ID("table_sys_id", "CamelServiceNowTableSysId", String.class),
    PARAM_ENCRYPTION_CONTEXT("encryption_context", "CamelServiceNowEncryptionContext", String.class),
    SYSPARM_CATEGORY("sysparm_category", "CamelServiceNowCategory", String.class),
    SYSPARM_TYPE("sysparm_type", "CamelServiceNowType", String.class),
    SYSPARM_CATALOG("sysparm_catalog", "CamelServiceNowCatalog", String.class),
    SYSPARM_QUERY("sysparm_query", "CamelServiceNowQuery", String.class),
    SYSPARM_DISPLAY_VALUE("sysparm_display_value", "CamelServiceNowDisplayValue", String.class, ServiceNowConfiguration::getDisplayValue),
    SYSPARM_INPUT_DISPLAY_VALUE("sysparm_input_display_value", "CamelServiceNowInputDisplayValue", Boolean.class, ServiceNowConfiguration::getInputDisplayValue),
    SYSPARM_EXCLUDE_REFERENCE_LINK("sysparm_exclude_reference_link", "CamelServiceNowExcludeReferenceLink", Boolean.class, ServiceNowConfiguration::getExcludeReferenceLink),
    SYSPARM_FIELDS("sysparm_fields", "CamelServiceNowFields", String.class),
    SYSPARM_LIMIT("sysparm_limit", "CamelServiceNowLimit", Integer.class),
    SYSPARM_TEXT("sysparm_text", "CamelServiceNowText", String.class),
    SYSPARM_OFFSET("sysparm_offset", "CamelServiceNowOffset", Integer.class),
    SYSPARM_VIEW("sysparm_view", "CamelServiceNowView", String.class),
    SYSPARM_SUPPRESS_AUTO_SYS_FIELD("sysparm_suppress_auto_sys_field", "CamelServiceNowSuppressAutoSysField", Boolean.class, ServiceNowConfiguration::getSuppressAutoSysField),
    SYSPARM_SUPPRESS_PAGINATION_HEADER("sysparm_suppress_pagination_header", "CamelServiceNowSuppressPaginationHeader", Boolean.class, ServiceNowConfiguration::getSuppressPaginationHeader),
    SYSPARM_MIN_FIELDS("sysparm_min_fields", "CamelServiceNowMinFields", String.class),
    SYSPARM_MAX_FIELDS("sysparm_max_fields", "CamelServiceNowMaxFields", String.class),
    SYSPARM_SUM_FIELDS("sysparm_sum_fields", "CamelServiceNowSumFields", String.class),
    SYSPARM_AVG_FIELDS("sysparm_avg_fields", "CamelServiceNowAvgFields", String.class),
    SYSPARM_COUNT("sysparm_count", "CamelServiceNowCount", Boolean.class),
    SYSPARM_GROUP_BY("sysparm_group_by", "CamelServiceNowGroupBy", String.class),
    SYSPARM_ORDER_BY("sysparm_order_by", "CamelServiceNowOrderBy", String.class),
    SYSPARM_HAVING("sysparm_having", "CamelServiceNowHaving", String.class),
    SYSPARM_UUID("sysparm_uuid", "CamelServiceNowUUID", String.class),
    SYSPARM_BREAKDOWN("sysparm_breakdown", "CamelServiceNowBreakdown", String.class),
    SYSPARM_INCLUDE_SCORES("sysparm_include_scores", "CamelServiceNowIncludeScores", Boolean.class, ServiceNowConfiguration::getIncludeScores),
    SYSPARM_INCLUDE_SCORE_NOTES("sysparm_include_score_notes", "CamelServiceNowIncludeScoreNotes", Boolean.class, ServiceNowConfiguration::getIncludeScoreNotes),
    SYSPARM_INCLUDE_AGGREGATES("sysparm_include_aggregates", "CamelServiceNowIncludeAggregates", Boolean.class, ServiceNowConfiguration::getIncludeAggregates),
    SYSPARM_INCLUDE_AVAILABLE_BREAKDOWNS("sysparm_include_available_breakdowns", "CamelServiceNowIncludeAvailableBreakdowns", Boolean.class, ServiceNowConfiguration::getIncludeAvailableBreakdowns),
    SYSPARM_INCLUDE_AVAILABLE_AGGREGATES("sysparm_include_available_aggregates", "CamelServiceNowIncludeAvailableAggregates", Boolean.class, ServiceNowConfiguration::getIncludeAvailableAggregates),
    SYSPARM_FAVORITES("sysparm_favorites", "CamelServiceNowFavorites", Boolean.class, ServiceNowConfiguration::getFavorites),
    SYSPARM_KEY("sysparm_key", "CamelServiceNowKey", Boolean.class, ServiceNowConfiguration::getKey),
    SYSPARM_TARGET("sysparm_target", "CamelServiceNowTarget", Boolean.class, ServiceNowConfiguration::getTarget),
    SYSPARM_DISPLAY("sysparm_display", "CamelServiceNowDisplay", String.class, ServiceNowConfiguration::getDisplay),
    SYSPARM_PER_PAGE("sysparm_per_page", "CamelServiceNowPerPage", Integer.class, ServiceNowConfiguration::getPerPage),
    SYSPARM_SORT_BY("sysparm_sortby", "CamelServiceNowSortBy", String.class, ServiceNowConfiguration::getSortBy),
    SYSPARM_SORT_DIR("sysparm_sortdir", "CamelServiceNowSortDir", String.class, ServiceNowConfiguration::getSortDir),
    SYSPARM_CONTAINS("sysparm_contains", "CamelServiceNowContains", String.class),
    SYSPARM_TAGS("sysparm_tags", "CamelServiceNowTags", String.class),
    SYSPARM_PAGE("sysparm_page", "CamelServiceNowPage", String.class),
    SYSPARM_ELEMENTS_FILTER("sysparm_elements_filter", "CamelServiceNowElementsFilter", String.class),
    SYSPARM_BREAKDOWN_RELATION("sysparm_breakdown_relation", "CamelServiceNowBreakdownRelation", String.class),
    SYSPARM_DATA_SOURCE("sysparm_data_source", "CamelServiceNowDataSource", String.class),
    SYSPARM_TOP_LEVEL_ONLY("sysparm_top_level_only", "CamelServiceNowTopLevelOnly", Boolean.class, ServiceNowConfiguration::getTopLevelOnly);

    private final String id;
    private final String header;
    private final Class<?> type;
    private final Function<ServiceNowConfiguration, ?> defaultValueSupplier;

    ServiceNowParams(String id, String header, Class<?> type) {
        this(id, header, type, null);
    }

    ServiceNowParams(String id, String header, Class<?> type, Function<ServiceNowConfiguration, ?> defaultValueSupplier) {
        ObjectHelper.notNull(id, "ServiceNowSysParam (id)");
        ObjectHelper.notNull(header, "ServiceNowSysParam (header)");
        ObjectHelper.notNull(type, "ServiceNowSysParam (type)");

        this.id = id;
        this.header = header.startsWith(ServiceNowConstants.CAMEL_HEADER_PREFIX)
            ? header
            : ServiceNowConstants.CAMEL_HEADER_PREFIX + StringHelper.capitalize(header);

        this.type = type;
        this.defaultValueSupplier = defaultValueSupplier;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getHeader() {
        return header;
    }

    @Override
    public Class<?> getType() {
        return type;
    }

    @Override
    public Object getDefaultValue(ServiceNowConfiguration configuration) {
        return defaultValueSupplier != null ? defaultValueSupplier.apply(configuration) : null;
    }

    @Override
    public Object getHeaderValue(Message message) {
        return message.getHeader(header, type);
    }

    @Override
    public Object getHeaderValue(Message message, ServiceNowConfiguration configuration) {
        return message.getHeader(header, getDefaultValue(configuration), type);
    }
}
