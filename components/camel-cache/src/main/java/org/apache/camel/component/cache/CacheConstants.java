/**
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
package org.apache.camel.component.cache;

/**
 * Constants used in this module
 */
public interface CacheConstants {
    String CACHE_HEADER_PREFIX = "CamelCache";
    String CACHE_OPERATION = CACHE_HEADER_PREFIX + "Operation";
    String CACHE_KEY = CACHE_HEADER_PREFIX + "Key";
    String CACHE_ELEMENT_WAS_FOUND = CACHE_HEADER_PREFIX + "ElementWasFound";

    String CACHE_ELEMENT_EXPIRY_TTL = CACHE_HEADER_PREFIX + "TimeToLive";
    String CACHE_ELEMENT_EXPIRY_IDLE = CACHE_HEADER_PREFIX + "TimeToIdle";
    String CACHE_ELEMENT_EXPIRY_ETERNAL = CACHE_HEADER_PREFIX + "Eternal";

    String CACHE_OPERATION_URL_ADD = "Add";
    String CACHE_OPERATION_URL_UPDATE = "Update";
    String CACHE_OPERATION_URL_DELETE = "Delete";
    String CACHE_OPERATION_URL_DELETEALL = "DeleteAll";
    String CACHE_OPERATION_URL_GET = "Get";
    String CACHE_OPERATION_URL_CHECK = "Check";

    String CACHE_OPERATION_ADD = CACHE_HEADER_PREFIX + CACHE_OPERATION_URL_ADD;
    String CACHE_OPERATION_UPDATE = CACHE_HEADER_PREFIX + CACHE_OPERATION_URL_UPDATE;
    String CACHE_OPERATION_DELETE = CACHE_HEADER_PREFIX + CACHE_OPERATION_URL_DELETE;
    String CACHE_OPERATION_DELETEALL = CACHE_HEADER_PREFIX + CACHE_OPERATION_URL_DELETEALL;
    String CACHE_OPERATION_GET = CACHE_HEADER_PREFIX + CACHE_OPERATION_URL_GET;
    String CACHE_OPERATION_CHECK = CACHE_HEADER_PREFIX + CACHE_OPERATION_URL_CHECK;
}