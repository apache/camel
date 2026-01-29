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
package org.apache.camel.component.docling;

/**
 * Constants for document metadata field names used when parsing metadata from Docling output. These constants support
 * multiple naming conventions (camelCase, snake_case, PascalCase) as different document parsers may use different
 * conventions.
 */
public final class DoclingMetadataFields {

    // Format field variations
    public static final String FORMAT = "format";
    public static final String FORMAT_PASCAL = "Format";

    // Document structure fields
    public static final String METADATA = "metadata";
    public static final String DOCUMENT = "document";
    public static final String NAME = "name";
    public static final String MAIN_TEXT = "main-text";

    // Page count field variations
    public static final String PAGES = "pages";
    public static final String NUM_PAGES = "num_pages";
    public static final String PAGE_COUNT = "page_count";

    /**
     * Array of all standard metadata field names (lowercase versions) for checking.
     */
    public static final String[] STANDARD_FIELDS = { FORMAT };

    /**
     * Check if a field name is a standard metadata field.
     *
     * @param  fieldName the field name to check
     * @return           true if it's a standard field
     */
    public static boolean isStandardField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase();
        return lower.equals(FORMAT);
    }

    private DoclingMetadataFields() {
        // Utility class - no instances
    }

}
