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
package org.apache.camel.component.stitch;

public final class StitchConstants {
    private static final String HEADER_PREFIX = "CamelStitch";
    // headers evaluated by producer
    public static final String TABLE_NAME = HEADER_PREFIX + "TableName";
    public static final String SCHEMA = HEADER_PREFIX + "Schema";
    public static final String KEY_NAMES = HEADER_PREFIX + "KeyNames";
    // headers set by producer
    public static final String CODE = HEADER_PREFIX + "Code";
    public static final String HEADERS = HEADER_PREFIX + "Headers";
    public static final String STATUS = HEADER_PREFIX + "Status";

    private StitchConstants() {
    }
}
