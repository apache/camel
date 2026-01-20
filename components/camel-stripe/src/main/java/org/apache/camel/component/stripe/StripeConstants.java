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
package org.apache.camel.component.stripe;

import org.apache.camel.spi.Metadata;

public final class StripeConstants {

    public static final String HEADER_PREFIX = "CamelStripe";

    // Operations
    public static final String OPERATION_CHARGES = "charges";
    public static final String OPERATION_CUSTOMERS = "customers";
    public static final String OPERATION_PAYMENT_INTENTS = "paymentIntents";
    public static final String OPERATION_PAYMENT_METHODS = "paymentMethods";
    public static final String OPERATION_REFUNDS = "refunds";
    public static final String OPERATION_SUBSCRIPTIONS = "subscriptions";
    public static final String OPERATION_INVOICES = "invoices";
    public static final String OPERATION_PRODUCTS = "products";
    public static final String OPERATION_PRICES = "prices";
    public static final String OPERATION_BALANCE_TRANSACTIONS = "balanceTransactions";

    // Headers
    @Metadata(description = "The operation to perform", javaType = "String")
    public static final String OPERATION_HEADER = HEADER_PREFIX + "Operation";
    @Metadata(description = "The method to invoke (create, retrieve, update, delete, list, cancel, capture)",
              javaType = "String")
    public static final String METHOD_HEADER = HEADER_PREFIX + "Method";
    @Metadata(description = "The ID of the object to retrieve, update, or delete", javaType = "String")
    public static final String OBJECT_ID = HEADER_PREFIX + "ObjectId";

    // Methods
    public static final String METHOD_CREATE = "create";
    public static final String METHOD_RETRIEVE = "retrieve";
    public static final String METHOD_UPDATE = "update";
    public static final String METHOD_DELETE = "delete";
    public static final String METHOD_LIST = "list";
    public static final String METHOD_CANCEL = "cancel";
    public static final String METHOD_CAPTURE = "capture";

    private StripeConstants() {
    }
}
