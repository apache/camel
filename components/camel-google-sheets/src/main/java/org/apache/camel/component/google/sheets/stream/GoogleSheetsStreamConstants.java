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
package org.apache.camel.component.google.sheets.stream;

/**
 * Constants used in Camel Google Sheets Stream
 */
public final class GoogleSheetsStreamConstants {

    private static final String PROPERTY_PREFIX = "CamelGoogleSheets";

    public static final String SPREADSHEET_ID =  PROPERTY_PREFIX + "SpreadsheetId";
    public static final String SPREADSHEET_URL =  PROPERTY_PREFIX + "SpreadsheetUrl";
    public static final String MAJOR_DIMENSION = PROPERTY_PREFIX + "MajorDimension";
    public static final String RANGE = PROPERTY_PREFIX + "Range";
    public static final String RANGE_INDEX = PROPERTY_PREFIX + "RangeIndex";
    public static final String VALUE_INDEX = PROPERTY_PREFIX + "ValueIndex";

    /**
     * Prevent instantiation.
     */
    private GoogleSheetsStreamConstants() {
    }
}
