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

import org.apache.camel.spi.Metadata;

public final class ServiceNowConstants {
    public static final String COMPONENT_SCHEME = "servicenow";

    public static final String CAMEL_HEADER_PREFIX = "CamelServiceNow";

    @Metadata(description = "The REST API to call: table, aggregate, import, attachment, scorecards, misc, service_catalog, "
                            + "service_catalog_items, service_catalog_cart or service_catalog_categories. It replaces the "
                            + "resource option.",
              javaType = "String")
    public static final String RESOURCE = "CamelServiceNowResource";
    @Metadata(description = "The action to perform on the resource: retrieve, content, create, modify, delete, update or "
                            + "upload. For the table resource, modify replaces a record (PUT) and update changes only the "
                            + "fields in the body (PATCH).",
              javaType = "String")
    public static final String ACTION = "CamelServiceNowAction";
    @Metadata(description = "The subject that selects the operation when a resource has several for the same action: "
                            + "categories, cart, producer, submit_guide, checkout_guide, performance_analytics, "
                            + "user_role_inheritance, identify_reconcile, delivery_address or checkout. The scorecards and "
                            + "misc resources always need one.",
              javaType = "String")
    public static final String ACTION_SUBJECT = "CamelServiceNowActionSubject";
    @Metadata(description = "The class used as both the request model and the response model. It replaces the models option. "
                            + "After a call, the producer sets it to the name of the response model class if it is not set.",
              javaType = "Class")
    public static final String MODEL = "CamelServiceNowModel";
    @Metadata(description = "The class of the request body. The producer fails when the body is not an instance of it. It "
                            + "replaces the CamelServiceNowModel header and the requestModels option.",
              javaType = "Class")
    public static final String REQUEST_MODEL = "CamelServiceNowRequestModel";
    @Metadata(description = "The class that the producer converts the result to. It replaces the CamelServiceNowModel header "
                            + "and the responseModels option. After a call, the producer sets it to the name of the response "
                            + "model class if it is not set.",
              javaType = "Class")
    public static final String RESPONSE_MODEL = "CamelServiceNowResponseModel";
    public static final String OFFSET_NEXT = "CamelServiceNowOffsetNext";
    public static final String OFFSET_PREV = "CamelServiceNowOffsetPrev";
    public static final String OFFSET_FIRST = "CamelServiceNowOffsetFirst";
    public static final String OFFSET_LAST = "CamelServiceNowOffsetLast";
    @Metadata(description = "The content type of the file that the upload action of the attachment resource sends, which the "
                            + "upload requires. The producer sets it to the Content-Type of the response.",
              javaType = "String")
    public static final String CONTENT_TYPE = "CamelServiceNowContentType";
    public static final String CONTENT_ENCODING = "CamelServiceNowContentEncoding";
    @Metadata(description = "The metadata of the attachment as a Map, which the producer reads from the "
                            + "X-Attachment-Metadata header of the response of the content action.",
              javaType = "Map")
    public static final String CONTENT_META = "CamelServiceNowContentMeta";
    @Metadata(description = "The fields of the response other than result, as a Map of their text values.", javaType = "Map")
    public static final String RESPONSE_META = "CamelServiceNowResponseMeta";
    @Metadata(description = "The version of the REST API, for example v1. It replaces the apiVersion option.",
              javaType = "String")
    public static final String API_VERSION = "CamelServiceNowApiVersion";
    @Metadata(description = "The class of the body that the producer set from the result.", javaType = "Class")
    public static final String RESPONSE_TYPE = "CamelServiceNowResponseType";
    @Metadata(description = "Whether the create action of the import resource returns the record inserted into the target "
                            + "table instead of the import set result. It replaces the retrieveTargetRecordOnImport option.",
              javaType = "Boolean")
    public static final String RETRIEVE_TARGET_RECORD = "CamelServiceNowRetrieveTargetRecord";

    @Metadata(description = "The name of the table. It replaces the table option. For the upload action of the attachment "
                            + "resource, it is the table of the record to attach the file to (table_name).",
              javaType = "String")
    public static final String PARAM_TABLE_NAME = "CamelServiceNowTable";
    @Metadata(description = "The sys_id of the record, attachment, catalog, category, item or cart to act on. Without it, "
                            + "the retrieve action of the table and attachment resources lists the records.",
              javaType = "String")
    public static final String PARAM_SYS_ID = "CamelServiceNowSysId";
    @Metadata(description = "The sys_id of the user whose roles the user_role_inheritance subject of the misc resource "
                            + "returns (user_sysid).",
              javaType = "String")
    public static final String PARAM_USER_SYS_ID = "CamelServiceNowUserSysId";
    @Metadata(description = "The ID of the user whose delivery address the delivery_address subject of the "
                            + "service_catalog_cart resource returns.",
              javaType = "String")
    public static final String PARAM_USER_ID = "CamelServiceNowUserId";
    @Metadata(description = "The ID of the cart item that the update action of the service_catalog_cart resource changes.",
              javaType = "String")
    public static final String PARAM_CART_ITEM_ID = "CamelServiceNowCartItemId";
    @Metadata(description = "The name of the file that the upload action of the attachment resource creates (file_name).",
              javaType = "String")
    public static final String PARAM_FILE_NAME = "CamelServiceNowFileName";
    @Metadata(description = "The sys_id of the record that the upload action of the attachment resource attaches the file to "
                            + "(table_sys_id).",
              javaType = "String")
    public static final String PARAM_TABLE_SYS_ID = "CamelServiceNowTableSysId";
    @Metadata(description = "The sys_id of the encryption context that the upload action of the attachment resource encrypts "
                            + "the file with (encryption_context).",
              javaType = "String")
    public static final String PARAM_ENCRYPTION_CONTEXT = "CamelServiceNowEncryptionContext";
    @Metadata(description = "The sys_id of the category to filter the service catalog items by (sysparm_category).",
              javaType = "String")
    public static final String SYSPARM_CATEGORY = "CamelServiceNowCategory";
    @Metadata(description = "The type of the service catalog items to return (sysparm_type).", javaType = "String")
    public static final String SYSPARM_TYPE = "CamelServiceNowType";
    @Metadata(description = "The sys_id of the catalog to filter the service catalog items by (sysparm_catalog).",
              javaType = "String")
    public static final String SYSPARM_CATALOG = "CamelServiceNowCatalog";
    @Metadata(description = "The encoded query that filters the records, for example active=true^priority=1 (sysparm_query).",
              javaType = "String")
    public static final String SYSPARM_QUERY = "CamelServiceNowQuery";
    @Metadata(description = "Whether ServiceNow returns the display values (true), the actual values (false) or both (all) "
                            + "of the fields. It replaces the displayValue option.",
              javaType = "String", defaultValue = "false")
    public static final String SYSPARM_DISPLAY_VALUE = "CamelServiceNowDisplayValue";
    @Metadata(description = "Whether ServiceNow treats the field values of a create or change as display values. It replaces "
                            + "the inputDisplayValue option.",
              javaType = "Boolean", defaultValue = "false")
    public static final String SYSPARM_INPUT_DISPLAY_VALUE = "CamelServiceNowInputDisplayValue";
    @Metadata(description = "Whether ServiceNow leaves out the Table API links of reference fields. It replaces the "
                            + "excludeReferenceLink option.",
              javaType = "Boolean",
              defaultValue = "false")
    public static final String SYSPARM_EXCLUDE_REFERENCE_LINK = "CamelServiceNowExcludeReferenceLink";
    @Metadata(description = "The comma-separated names of the fields to return (sysparm_fields).", javaType = "String")
    public static final String SYSPARM_FIELDS = "CamelServiceNowFields";
    @Metadata(description = "The maximum number of records to return (sysparm_limit).", javaType = "Integer")
    public static final String SYSPARM_LIMIT = "CamelServiceNowLimit";
    @Metadata(description = "The text to search the service catalog items for (sysparm_text).", javaType = "String")
    public static final String SYSPARM_TEXT = "CamelServiceNowText";
    @Metadata(description = "The index of the first record to return, for paging (sysparm_offset).", javaType = "Integer")
    public static final String SYSPARM_OFFSET = "CamelServiceNowOffset";
    @Metadata(description = "The UI view whose fields to return: desktop, mobile or both (sysparm_view).", javaType = "String")
    public static final String SYSPARM_VIEW = "CamelServiceNowView";
    @Metadata(description = "Whether ServiceNow does not generate the system fields when a record is created or changed. It "
                            + "replaces the suppressAutoSysField option.",
              javaType = "Boolean", defaultValue = "false")
    public static final String SYSPARM_SUPPRESS_AUTO_SYS_FIELD = "CamelServiceNowSuppressAutoSysField";
    @Metadata(description = "Whether ServiceNow leaves out the Link header of the response, from which the producer reads "
                            + "the paging offsets. It replaces the suppressPaginationHeader option.",
              javaType = "Boolean")
    public static final String SYSPARM_SUPPRESS_PAGINATION_HEADER = "CamelServiceNowSuppressPaginationHeader";
    @Metadata(description = "The comma-separated fields whose minimum the aggregate resource returns (sysparm_min_fields).",
              javaType = "String")
    public static final String SYSPARM_MIN_FIELDS = "CamelServiceNowMinFields";
    @Metadata(description = "The comma-separated fields whose maximum the aggregate resource returns (sysparm_max_fields).",
              javaType = "String")
    public static final String SYSPARM_MAX_FIELDS = "CamelServiceNowMaxFields";
    @Metadata(description = "The comma-separated fields whose sum the aggregate resource returns (sysparm_sum_fields).",
              javaType = "String")
    public static final String SYSPARM_SUM_FIELDS = "CamelServiceNowSumFields";
    @Metadata(description = "The comma-separated fields whose average the aggregate resource returns (sysparm_avg_fields).",
              javaType = "String")
    public static final String SYSPARM_AVG_FIELDS = "CamelServiceNowAvgFields";
    @Metadata(description = "Whether the aggregate resource returns the number of records (sysparm_count).",
              javaType = "Boolean")
    public static final String SYSPARM_COUNT = "CamelServiceNowCount";
    @Metadata(description = "The comma-separated fields to group the aggregate results by (sysparm_group_by).",
              javaType = "String")
    public static final String SYSPARM_GROUP_BY = "CamelServiceNowGroupBy";
    @Metadata(description = "The comma-separated fields to order the aggregate groups by (sysparm_order_by).",
              javaType = "String")
    public static final String SYSPARM_ORDER_BY = "CamelServiceNowOrderBy";
    @Metadata(description = "The condition on an aggregate that the groups must meet, as aggregate^field^operator^value, for "
                            + "example count^priority^>^3 (sysparm_having).",
              javaType = "String")
    public static final String SYSPARM_HAVING = "CamelServiceNowHaving";
    @Metadata(description = "The colon-separated sys_ids of the indicator, and optionally of its breakdown, element and "
                            + "aggregate, whose scorecards to return (sysparm_uuid).",
              javaType = "String")
    public static final String SYSPARM_UUID = "CamelServiceNowUUID";
    @Metadata(description = "The sys_id of the breakdown that organizes the returned scorecards (sysparm_breakdown).",
              javaType = "String")
    public static final String SYSPARM_BREAKDOWN = "CamelServiceNowBreakdown";
    @Metadata(description = "Whether to return all scores of a scorecard instead of only the most recent one. It replaces "
                            + "the includeScores option.",
              javaType = "Boolean")
    public static final String SYSPARM_INCLUDE_SCORES = "CamelServiceNowIncludeScores";
    @Metadata(description = "Whether to return the notes of each score. It replaces the includeScoreNotes option.",
              javaType = "Boolean")
    public static final String SYSPARM_INCLUDE_SCORE_NOTES = "CamelServiceNowIncludeScoreNotes";
    @Metadata(description = "Whether to return all available aggregates of an indicator, also when an aggregate is already "
                            + "applied. It replaces the includeAggregates option.",
              javaType = "Boolean")
    public static final String SYSPARM_INCLUDE_AGGREGATES = "CamelServiceNowIncludeAggregates";
    @Metadata(description = "Whether to return all available breakdowns of an indicator. It replaces the "
                            + "includeAvailableBreakdowns option.",
              javaType = "Boolean")
    public static final String SYSPARM_INCLUDE_AVAILABLE_BREAKDOWNS = "CamelServiceNowIncludeAvailableBreakdowns";
    @Metadata(description = "Whether to return all available aggregates of an indicator when no aggregate is applied. It "
                            + "replaces the includeAvailableAggregates option.",
              javaType = "Boolean")
    public static final String SYSPARM_INCLUDE_AVAILABLE_AGGREGATES = "CamelServiceNowIncludeAvailableAggregates";
    @Metadata(description = "Whether to return only the scorecards that are favorites of the user. It replaces the favorites "
                            + "option.",
              javaType = "Boolean")
    public static final String SYSPARM_FAVORITES = "CamelServiceNowFavorites";
    @Metadata(description = "Whether to return only the scorecards of key indicators. It replaces the key option.",
              javaType = "Boolean")
    public static final String SYSPARM_KEY = "CamelServiceNowKey";
    @Metadata(description = "Whether to return only the scorecards that have a target. It replaces the target option.",
              javaType = "Boolean")
    public static final String SYSPARM_TARGET = "CamelServiceNowTarget";
    @Metadata(description = "Which scorecards to return by the Display field of their indicator: true for only those with "
                            + "Display selected, all for any. It replaces the display option.",
              javaType = "String", defaultValue = "true")
    public static final String SYSPARM_DISPLAY = "CamelServiceNowDisplay";
    @Metadata(description = "The maximum number of scorecards to return per page, at most 100. It replaces the perPage "
                            + "option.",
              javaType = "Integer", defaultValue = "10")
    public static final String SYSPARM_PER_PAGE = "CamelServiceNowPerPage";
    @Metadata(description = "The value to sort the scorecards by. It replaces the sortBy option.",
              javaType = "String")
    public static final String SYSPARM_SORT_BY = "CamelServiceNowSortBy";
    @Metadata(description = "The sort direction of the scorecards, asc or desc. It replaces the sortDir option.",
              javaType = "String")
    public static final String SYSPARM_SORT_DIR = "CamelServiceNowSortDir";
    @Metadata(description = "The comma-separated names or descriptions of the indicators whose scorecards to return "
                            + "(sysparm_contains).",
              javaType = "String")
    public static final String SYSPARM_CONTAINS = "CamelServiceNowContains";
    @Metadata(description = "The comma-separated sys_ids of the indicator groups whose scorecards to return (sysparm_tags).",
              javaType = "String")
    public static final String SYSPARM_TAGS = "CamelServiceNowTags";
    @Metadata(description = "The number of the page of scorecards to return, with perPage scorecards per page "
                            + "(sysparm_page).",
              javaType = "String")
    public static final String SYSPARM_PAGE = "CamelServiceNowPage";
    @Metadata(description = "The sys_id of the elements filter to apply to the returned scorecards "
                            + "(sysparm_elements_filter).",
              javaType = "String")
    public static final String SYSPARM_ELEMENTS_FILTER = "CamelServiceNowElementsFilter";
    @Metadata(description = "The sys_id of the breakdown relation to break down the returned scorecards by "
                            + "(sysparm_breakdown_relation).",
              javaType = "String")
    public static final String SYSPARM_BREAKDOWN_RELATION = "CamelServiceNowBreakdownRelation";
    @Metadata(description = "The source of the configuration item data that the identify_reconcile subject of the misc "
                            + "resource sends (sysparm_data_source).",
              javaType = "String")
    public static final String SYSPARM_DATA_SOURCE = "CamelServiceNowDataSource";
    @Metadata(description = "Whether to return only the categories whose parent is the catalog. It replaces the topLevelOnly "
                            + "option.",
              javaType = "Boolean")
    public static final String SYSPARM_TOP_LEVEL_ONLY = "CamelServiceNowTopLevelOnly";

    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";
    public static final String DEFAULT_DATE_TIME_FORMAT = DEFAULT_DATE_FORMAT + " " + DEFAULT_TIME_FORMAT;

    public static final String ATTACHMENT_META_HEADER = "X-Attachment-Metadata";

    public static final String RESOURCE_TABLE = "table";
    public static final String RESOURCE_AGGREGATE = "aggregate";
    public static final String RESOURCE_IMPORT = "import";
    public static final String RESOURCE_ATTACHMENT = "attachment";
    public static final String RESOURCE_SCORECARDS = "scorecards";
    public static final String RESOURCE_MISC = "misc";
    public static final String RESOURCE_SERVICE_CATALOG = "service_catalog";
    public static final String RESOURCE_SERVICE_CATALOG_ITEMS = "service_catalog_items";
    public static final String RESOURCE_SERVICE_CATALOG_CARTS = "service_catalog_cart";
    public static final String RESOURCE_SERVICE_CATALOG_CATEGORIES = "service_catalog_categories";

    public static final String ACTION_RETRIEVE = "retrieve";
    public static final String ACTION_CONTENT = "content";
    public static final String ACTION_CREATE = "create";
    public static final String ACTION_MODIFY = "modify";
    public static final String ACTION_DELETE = "delete";
    public static final String ACTION_UPDATE = "update";
    public static final String ACTION_UPLOAD = "upload";

    public static final String ACTION_SUBJECT_CATEGORIES = "categories";
    public static final String ACTION_SUBJECT_CART = "cart";
    public static final String ACTION_SUBJECT_PRODUCER = "producer";
    public static final String ACTION_SUBJECT_GUIDE = "guide";
    public static final String ACTION_SUBJECT_SUBMIT_GUIDE = "submit_guide";
    public static final String ACTION_SUBJECT_CHECKOUT_GUIDE = "checkout_guide";
    public static final String ACTION_SUBJECT_PERFORMANCE_ANALYTICS = "performance_analytics";
    public static final String ACTION_SUBJECT_USER_ROLE_INHERITANCE = "user_role_inheritance";
    public static final String ACTION_SUBJECT_IDENTIFY_RECONCILE = "identify_reconcile";
    public static final String ACTION_SUBJECT_DELIVERY_ADDRESS = "delivery_address";
    public static final String ACTION_SUBJECT_CHECKOUT = "checkout";

    public static final String LINK_NEXT = "next";
    public static final String LINK_PREV = "prev";
    public static final String LINK_FIRST = "first";
    public static final String LINK_LAST = "last";

    private ServiceNowConstants() {
    }
}
