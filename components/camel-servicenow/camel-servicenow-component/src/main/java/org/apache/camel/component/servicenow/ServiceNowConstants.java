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

public final class ServiceNowConstants {
    public static final String COMPONENT_SCHEME = "servicenow";

    public static final String CAMEL_HEADER_PREFIX = "CamelServiceNow";

    public static final String RESOURCE = "CamelServiceNowResource";
    public static final String ACTION = "CamelServiceNowAction";
    public static final String ACTION_SUBJECT = "CamelServiceNowActionSubject";
    public static final String MODEL = "CamelServiceNowModel";
    public static final String REQUEST_MODEL = "CamelServiceNowRequestModel";
    public static final String RESPONSE_MODEL = "CamelServiceNowResponseModel";
    public static final String OFFSET_NEXT = "CamelServiceNowOffsetNext";
    public static final String OFFSET_PREV = "CamelServiceNowOffsetPrev";
    public static final String OFFSET_FIRST = "CamelServiceNowOffsetFirst";
    public static final String OFFSET_LAST = "CamelServiceNowOffsetLast";
    public static final String CONTENT_TYPE = "CamelServiceNowContentType";
    public static final String CONTENT_ENCODING = "CamelServiceNowContentEncoding";
    public static final String CONTENT_META = "CamelServiceNowContentMeta";
    public static final String RESPONSE_META = "CamelServiceNowResponseMeta";
    public static final String API_VERSION = "CamelServiceNowApiVersion";
    public static final String RESPONSE_TYPE = "CamelServiceNowResponseType";
    public static final String RETRIEVE_TARGET_RECORD = "CamelServiceNowRetrieveTargetRecord";

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
