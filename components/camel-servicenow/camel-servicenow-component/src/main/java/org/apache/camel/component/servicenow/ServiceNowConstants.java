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

    @Metadata(description = "The resource to access", javaType = "String")
    public static final String RESOURCE = "CamelServiceNowResource";
    @Metadata(description = "The action to perform", javaType = "String")
    public static final String ACTION = "CamelServiceNowAction";
    @Metadata(description = "The action subject", javaType = "String")
    public static final String ACTION_SUBJECT = "CamelServiceNowActionSubject";
    @Metadata(description = "The data model", javaType = "Class")
    public static final String MODEL = "CamelServiceNowModel";
    @Metadata(description = "The request data model", javaType = "Class")
    public static final String REQUEST_MODEL = "CamelServiceNowRequestModel";
    @Metadata(description = "The response data model", javaType = "Class")
    public static final String RESPONSE_MODEL = "CamelServiceNowResponseModel";
    public static final String OFFSET_NEXT = "CamelServiceNowOffsetNext";
    public static final String OFFSET_PREV = "CamelServiceNowOffsetPrev";
    public static final String OFFSET_FIRST = "CamelServiceNowOffsetFirst";
    public static final String OFFSET_LAST = "CamelServiceNowOffsetLast";
    @Metadata(description = "The content type", javaType = "String")
    public static final String CONTENT_TYPE = "CamelServiceNowContentType";
    public static final String CONTENT_ENCODING = "CamelServiceNowContentEncoding";
    @Metadata(description = "The content meta", javaType = "Map")
    public static final String CONTENT_META = "CamelServiceNowContentMeta";
    @Metadata(description = "The response meta", javaType = "Map")
    public static final String RESPONSE_META = "CamelServiceNowResponseMeta";
    @Metadata(description = "The REST API version", javaType = "String")
    public static final String API_VERSION = "CamelServiceNowApiVersion";
    @Metadata(description = "The type of the response", javaType = "Class")
    public static final String RESPONSE_TYPE = "CamelServiceNowResponseType";
    @Metadata(description = "Set this parameter to true to retrieve the target record.", javaType = "Boolean")
    public static final String RETRIEVE_TARGET_RECORD = "CamelServiceNowRetrieveTargetRecord";

    @Metadata(description = "The default table", javaType = "String")
    public static final String PARAM_TABLE_NAME = "CamelServiceNowTable";
    @Metadata(description = "The sys id", javaType = "String")
    public static final String PARAM_SYS_ID = "CamelServiceNowSysId";
    @Metadata(description = "The user sys id", javaType = "String")
    public static final String PARAM_USER_SYS_ID = "CamelServiceNowUserSysId";
    @Metadata(description = "The user id", javaType = "String")
    public static final String PARAM_USER_ID = "CamelServiceNowUserId";
    @Metadata(description = "The cart item id", javaType = "String")
    public static final String PARAM_CART_ITEM_ID = "CamelServiceNowCartItemId";
    @Metadata(description = "The file name", javaType = "String")
    public static final String PARAM_FILE_NAME = "CamelServiceNowFileName";
    @Metadata(description = "The table sys id", javaType = "String")
    public static final String PARAM_TABLE_SYS_ID = "CamelServiceNowTableSysId";
    @Metadata(description = "The encryption context", javaType = "String")
    public static final String PARAM_ENCRYPTION_CONTEXT = "CamelServiceNowEncryptionContext";
    @Metadata(description = "The sys param category", javaType = "String")
    public static final String SYSPARM_CATEGORY = "CamelServiceNowCategory";
    @Metadata(description = "The sys param type", javaType = "String")
    public static final String SYSPARM_TYPE = "CamelServiceNowType";
    @Metadata(description = "The sys param catalog", javaType = "String")
    public static final String SYSPARM_CATALOG = "CamelServiceNowCatalog";
    @Metadata(description = "The sys param query", javaType = "String")
    public static final String SYSPARM_QUERY = "CamelServiceNowQuery";
    @Metadata(description = "Return the display value (true), actual value (false), or both (all) for reference fields",
              javaType = "String", defaultValue = "false")
    public static final String SYSPARM_DISPLAY_VALUE = "CamelServiceNowDisplayValue";
    @Metadata(description = "True to set raw value of input fields", javaType = "Boolean", defaultValue = "false")
    public static final String SYSPARM_INPUT_DISPLAY_VALUE = "CamelServiceNowInputDisplayValue";
    @Metadata(description = "True to exclude Table API links for reference fields", javaType = "Boolean",
              defaultValue = "false")
    public static final String SYSPARM_EXCLUDE_REFERENCE_LINK = "CamelServiceNowExcludeReferenceLink";
    @Metadata(description = "The sys param fields", javaType = "String")
    public static final String SYSPARM_FIELDS = "CamelServiceNowFields";
    @Metadata(description = "The sys param limit", javaType = "Integer")
    public static final String SYSPARM_LIMIT = "CamelServiceNowLimit";
    @Metadata(description = "The sys param text", javaType = "String")
    public static final String SYSPARM_TEXT = "CamelServiceNowText";
    @Metadata(description = "The sys param offset", javaType = "Integer")
    public static final String SYSPARM_OFFSET = "CamelServiceNowOffset";
    @Metadata(description = "The sys param view", javaType = "String")
    public static final String SYSPARM_VIEW = "CamelServiceNowView";
    @Metadata(description = "True to suppress auto generation of system fields", javaType = "Boolean", defaultValue = "false")
    public static final String SYSPARM_SUPPRESS_AUTO_SYS_FIELD = "CamelServiceNowSuppressAutoSysField";
    @Metadata(description = "Set this value to true to remove the Link header from the response. The Link header allows you to request\n"
                            +
                            " additional pages of data when the number of records matching your query exceeds the query limit",
              javaType = "Boolean")
    public static final String SYSPARM_SUPPRESS_PAGINATION_HEADER = "CamelServiceNowSuppressPaginationHeader";
    @Metadata(description = "The sys param min fields", javaType = "String")
    public static final String SYSPARM_MIN_FIELDS = "CamelServiceNowMinFields";
    @Metadata(description = "The sys param max fields", javaType = "String")
    public static final String SYSPARM_MAX_FIELDS = "CamelServiceNowMaxFields";
    @Metadata(description = "The sys param sum fields", javaType = "String")
    public static final String SYSPARM_SUM_FIELDS = "CamelServiceNowSumFields";
    @Metadata(description = "The sys param avg fields", javaType = "String")
    public static final String SYSPARM_AVG_FIELDS = "CamelServiceNowAvgFields";
    @Metadata(description = "The sys param count", javaType = "Boolean")
    public static final String SYSPARM_COUNT = "CamelServiceNowCount";
    @Metadata(description = "The sys param group by", javaType = "String")
    public static final String SYSPARM_GROUP_BY = "CamelServiceNowGroupBy";
    @Metadata(description = "The sys param order by", javaType = "String")
    public static final String SYSPARM_ORDER_BY = "CamelServiceNowOrderBy";
    @Metadata(description = "The sys param having", javaType = "String")
    public static final String SYSPARM_HAVING = "CamelServiceNowHaving";
    @Metadata(description = "The sys param UUID", javaType = "String")
    public static final String SYSPARM_UUID = "CamelServiceNowUUID";
    @Metadata(description = "The sys param breakdown", javaType = "String")
    public static final String SYSPARM_BREAKDOWN = "CamelServiceNowBreakdown";
    @Metadata(description = "Set this parameter to true to return all scores for a scorecard. If a value is not specified, this parameter\n"
                            +
                            " defaults to false and returns only the most recent score value.",
              javaType = "Boolean")
    public static final String SYSPARM_INCLUDE_SCORES = "CamelServiceNowIncludeScores";
    @Metadata(description = "Set this parameter to true to return all notes associated with the score. The note element contains the note text\n"
                            +
                            " as well as the author and timestamp when the note was added.",
              javaType = "Boolean")
    public static final String SYSPARM_INCLUDE_SCORE_NOTES = "CamelServiceNowIncludeScoreNotes";
    @Metadata(description = "Set this parameter to true to always return all available aggregates for an indicator, including when an\n"
                            +
                            " aggregate has already been applied. If a value is not specified, this parameter defaults to false and returns no\n"
                            +
                            " aggregates.",
              javaType = "Boolean")
    public static final String SYSPARM_INCLUDE_AGGREGATES = "CamelServiceNowIncludeAggregates";
    @Metadata(description = "Set this parameter to true to return all available breakdowns for an indicator. If a value is not specified, this\n"
                            +
                            " parameter defaults to false and returns no breakdowns.",
              javaType = "Boolean")
    public static final String SYSPARM_INCLUDE_AVAILABLE_BREAKDOWNS = "CamelServiceNowIncludeAvailableBreakdowns";
    @Metadata(description = "Set this parameter to true to return all available aggregates for an indicator when no aggregate has been\n"
                            +
                            " applied. If a value is not specified, this parameter defaults to false and returns no aggregates.",
              javaType = "Boolean")
    public static final String SYSPARM_INCLUDE_AVAILABLE_AGGREGATES = "CamelServiceNowIncludeAvailableAggregates";
    @Metadata(description = "Set this parameter to true to return only scorecards that are favorites of the querying user.",
              javaType = "Boolean")
    public static final String SYSPARM_FAVORITES = "CamelServiceNowFavorites";
    @Metadata(description = "Set this parameter to true to return only scorecards for key indicators.", javaType = "Boolean")
    public static final String SYSPARM_KEY = "CamelServiceNowKey";
    @Metadata(description = "Set this parameter to true to return only scorecards that have a target.", javaType = "Boolean")
    public static final String SYSPARM_TARGET = "CamelServiceNowTarget";
    @Metadata(description = "Set this parameter to true to return only scorecards where the indicator Display field is selected. Set this\n"
                            +
                            " parameter to all to return scorecards with any Display field value.",
              javaType = "String", defaultValue = "true")
    public static final String SYSPARM_DISPLAY = "CamelServiceNowDisplay";
    @Metadata(description = "Enter the maximum number of scorecards each query can return. By default this value is 10, and the maximum is\n"
                            +
                            " 100.",
              javaType = "Integer", defaultValue = "10")
    public static final String SYSPARM_PER_PAGE = "CamelServiceNowPerPage";
    @Metadata(description = "Specify the value to use when sorting results. By default, queries sort records by value.",
              javaType = "String")
    public static final String SYSPARM_SORT_BY = "CamelServiceNowSortBy";
    @Metadata(description = "Specify the sort direction, ascending or descending. By default, queries sort records in descending order. Use\n"
                            +
                            " sysparm_sortdir=asc to sort in ascending order.",
              javaType = "String")
    public static final String SYSPARM_SORT_DIR = "CamelServiceNowSortDir";
    @Metadata(description = "The sys param contains.", javaType = "String")
    public static final String SYSPARM_CONTAINS = "CamelServiceNowContains";
    @Metadata(description = "The sys param tags.", javaType = "String")
    public static final String SYSPARM_TAGS = "CamelServiceNowTags";
    @Metadata(description = "The sys param page.", javaType = "String")
    public static final String SYSPARM_PAGE = "CamelServiceNowPage";
    @Metadata(description = "The sys param elements filter.", javaType = "String")
    public static final String SYSPARM_ELEMENTS_FILTER = "CamelServiceNowElementsFilter";
    @Metadata(description = "The sys param breakdown relation.", javaType = "String")
    public static final String SYSPARM_BREAKDOWN_RELATION = "CamelServiceNowBreakdownRelation";
    @Metadata(description = "The sys param data source.", javaType = "String")
    public static final String SYSPARM_DATA_SOURCE = "CamelServiceNowDataSource";
    @Metadata(description = "Gets only those categories whose parent is a catalog.", javaType = "Boolean")
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
