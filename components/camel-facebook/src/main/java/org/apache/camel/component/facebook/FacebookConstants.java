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
package org.apache.camel.component.facebook;

/**
 * Common constants.
 */
public interface FacebookConstants {

    // reading options property name and prefix for uri property
    String READING_PROPERTY = "reading";
    String READING_PREFIX = READING_PROPERTY + ".";

    // property name prefix for exchange 'in' headers
    String FACEBOOK_PROPERTY_PREFIX = "CamelFacebook.";

    String FACEBOOK_THREAD_PROFILE_NAME = "CamelFacebook";

    // date format used by Facebook Reading since and until fields
    String FACEBOOK_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";

    String RAW_JSON_HEADER = FACEBOOK_PROPERTY_PREFIX + "rawJSON";
}
