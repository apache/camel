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
    PARAM_SYS_ID("sys_id", ServiceNowConstants.PARAM_SYS_ID, String.class),
    PARAM_USER_SYS_ID("user_sysid", ServiceNowConstants.PARAM_USER_SYS_ID, String.class),
    PARAM_USER_ID("user_id", ServiceNowConstants.PARAM_USER_ID, String.class),
    PARAM_CART_ITEM_ID("cart_item_id", ServiceNowConstants.PARAM_CART_ITEM_ID, String.class),
    PARAM_FILE_NAME("file_name", ServiceNowConstants.PARAM_FILE_NAME, String.class),
    PARAM_TABLE_NAME("table_name", ServiceNowConstants.PARAM_TABLE_NAME, String.class),
    PARAM_TABLE_SYS_ID("table_sys_id", ServiceNowConstants.PARAM_TABLE_SYS_ID, String.class),
    PARAM_ENCRYPTION_CONTEXT("encryption_context", ServiceNowConstants.PARAM_ENCRYPTION_CONTEXT, String.class),
    SYSPARM_CATEGORY("sysparm_category", ServiceNowConstants.SYSPARM_CATEGORY, String.class),
    SYSPARM_TYPE("sysparm_type", ServiceNowConstants.SYSPARM_TYPE, String.class),
    SYSPARM_CATALOG("sysparm_catalog", ServiceNowConstants.SYSPARM_CATALOG, String.class),
    SYSPARM_QUERY("sysparm_query", ServiceNowConstants.SYSPARM_QUERY, String.class),
    SYSPARM_DISPLAY_VALUE("sysparm_display_value", ServiceNowConstants.SYSPARM_DISPLAY_VALUE, String.class,
                          ServiceNowConfiguration::getDisplayValue),
    SYSPARM_INPUT_DISPLAY_VALUE("sysparm_input_display_value", ServiceNowConstants.SYSPARM_INPUT_DISPLAY_VALUE, Boolean.class,
                                ServiceNowConfiguration::getInputDisplayValue),
    SYSPARM_EXCLUDE_REFERENCE_LINK("sysparm_exclude_reference_link", ServiceNowConstants.SYSPARM_EXCLUDE_REFERENCE_LINK,
                                   Boolean.class, ServiceNowConfiguration::getExcludeReferenceLink),
    SYSPARM_FIELDS("sysparm_fields", ServiceNowConstants.SYSPARM_FIELDS, String.class),
    SYSPARM_LIMIT("sysparm_limit", ServiceNowConstants.SYSPARM_LIMIT, Integer.class),
    SYSPARM_TEXT("sysparm_text", ServiceNowConstants.SYSPARM_TEXT, String.class),
    SYSPARM_OFFSET("sysparm_offset", ServiceNowConstants.SYSPARM_OFFSET, Integer.class),
    SYSPARM_VIEW("sysparm_view", ServiceNowConstants.SYSPARM_VIEW, String.class),
    SYSPARM_SUPPRESS_AUTO_SYS_FIELD("sysparm_suppress_auto_sys_field", ServiceNowConstants.SYSPARM_SUPPRESS_AUTO_SYS_FIELD,
                                    Boolean.class, ServiceNowConfiguration::getSuppressAutoSysField),
    SYSPARM_SUPPRESS_PAGINATION_HEADER("sysparm_suppress_pagination_header",
                                       ServiceNowConstants.SYSPARM_SUPPRESS_PAGINATION_HEADER, Boolean.class,
                                       ServiceNowConfiguration::getSuppressPaginationHeader),
    SYSPARM_MIN_FIELDS("sysparm_min_fields", ServiceNowConstants.SYSPARM_MIN_FIELDS, String.class),
    SYSPARM_MAX_FIELDS("sysparm_max_fields", ServiceNowConstants.SYSPARM_MAX_FIELDS, String.class),
    SYSPARM_SUM_FIELDS("sysparm_sum_fields", ServiceNowConstants.SYSPARM_SUM_FIELDS, String.class),
    SYSPARM_AVG_FIELDS("sysparm_avg_fields", ServiceNowConstants.SYSPARM_AVG_FIELDS, String.class),
    SYSPARM_COUNT("sysparm_count", ServiceNowConstants.SYSPARM_COUNT, Boolean.class),
    SYSPARM_GROUP_BY("sysparm_group_by", ServiceNowConstants.SYSPARM_GROUP_BY, String.class),
    SYSPARM_ORDER_BY("sysparm_order_by", ServiceNowConstants.SYSPARM_ORDER_BY, String.class),
    SYSPARM_HAVING("sysparm_having", ServiceNowConstants.SYSPARM_HAVING, String.class),
    SYSPARM_UUID("sysparm_uuid", ServiceNowConstants.SYSPARM_UUID, String.class),
    SYSPARM_BREAKDOWN("sysparm_breakdown", ServiceNowConstants.SYSPARM_BREAKDOWN, String.class),
    SYSPARM_INCLUDE_SCORES("sysparm_include_scores", ServiceNowConstants.SYSPARM_INCLUDE_SCORES, Boolean.class,
                           ServiceNowConfiguration::getIncludeScores),
    SYSPARM_INCLUDE_SCORE_NOTES("sysparm_include_score_notes", ServiceNowConstants.SYSPARM_INCLUDE_SCORE_NOTES, Boolean.class,
                                ServiceNowConfiguration::getIncludeScoreNotes),
    SYSPARM_INCLUDE_AGGREGATES("sysparm_include_aggregates", ServiceNowConstants.SYSPARM_INCLUDE_AGGREGATES, Boolean.class,
                               ServiceNowConfiguration::getIncludeAggregates),
    SYSPARM_INCLUDE_AVAILABLE_BREAKDOWNS("sysparm_include_available_breakdowns",
                                         ServiceNowConstants.SYSPARM_INCLUDE_AVAILABLE_BREAKDOWNS, Boolean.class,
                                         ServiceNowConfiguration::getIncludeAvailableBreakdowns),
    SYSPARM_INCLUDE_AVAILABLE_AGGREGATES("sysparm_include_available_aggregates",
                                         ServiceNowConstants.SYSPARM_INCLUDE_AVAILABLE_AGGREGATES, Boolean.class,
                                         ServiceNowConfiguration::getIncludeAvailableAggregates),
    SYSPARM_FAVORITES("sysparm_favorites", ServiceNowConstants.SYSPARM_FAVORITES, Boolean.class,
                      ServiceNowConfiguration::getFavorites),
    SYSPARM_KEY("sysparm_key", ServiceNowConstants.SYSPARM_KEY, Boolean.class, ServiceNowConfiguration::getKey),
    SYSPARM_TARGET("sysparm_target", ServiceNowConstants.SYSPARM_TARGET, Boolean.class, ServiceNowConfiguration::getTarget),
    SYSPARM_DISPLAY("sysparm_display", ServiceNowConstants.SYSPARM_DISPLAY, String.class, ServiceNowConfiguration::getDisplay),
    SYSPARM_PER_PAGE("sysparm_per_page", ServiceNowConstants.SYSPARM_PER_PAGE, Integer.class,
                     ServiceNowConfiguration::getPerPage),
    SYSPARM_SORT_BY("sysparm_sortby", ServiceNowConstants.SYSPARM_SORT_BY, String.class, ServiceNowConfiguration::getSortBy),
    SYSPARM_SORT_DIR("sysparm_sortdir", ServiceNowConstants.SYSPARM_SORT_DIR, String.class,
                     ServiceNowConfiguration::getSortDir),
    SYSPARM_CONTAINS("sysparm_contains", ServiceNowConstants.SYSPARM_CONTAINS, String.class),
    SYSPARM_TAGS("sysparm_tags", ServiceNowConstants.SYSPARM_TAGS, String.class),
    SYSPARM_PAGE("sysparm_page", ServiceNowConstants.SYSPARM_PAGE, String.class),
    SYSPARM_ELEMENTS_FILTER("sysparm_elements_filter", ServiceNowConstants.SYSPARM_ELEMENTS_FILTER, String.class),
    SYSPARM_BREAKDOWN_RELATION("sysparm_breakdown_relation", ServiceNowConstants.SYSPARM_BREAKDOWN_RELATION, String.class),
    SYSPARM_DATA_SOURCE("sysparm_data_source", ServiceNowConstants.SYSPARM_DATA_SOURCE, String.class),
    SYSPARM_TOP_LEVEL_ONLY("sysparm_top_level_only", ServiceNowConstants.SYSPARM_TOP_LEVEL_ONLY, Boolean.class,
                           ServiceNowConfiguration::getTopLevelOnly);

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
