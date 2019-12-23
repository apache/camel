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
package org.apache.camel.component.salesforce.api.utils;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.camel.component.salesforce.api.dto.AbstractDescribedSObjectBase;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.SObjectField;

public final class QueryHelper {

    private static final String[] NONE = new String[0];

    private QueryHelper() {
        // utility class
    }

    public static String[] fieldNamesOf(final AbstractDescribedSObjectBase object) {
        if (object == null) {
            return NONE;
        }

        final SObjectDescription description = object.description();
        final List<SObjectField> fields = description.getFields();

        return fields.stream().map(SObjectField::getName).toArray(String[]::new);
    }

    public static String[] filteredFieldNamesOf(final AbstractDescribedSObjectBase object, final Predicate<SObjectField> filter) {
        if (object == null) {
            return NONE;
        }

        if (filter == null) {
            return fieldNamesOf(object);
        }

        final SObjectDescription description = object.description();
        final List<SObjectField> fields = description.getFields();

        return fields.stream().filter(filter).map(SObjectField::getName).toArray(String[]::new);
    }

    public static String queryToFetchAllFieldsOf(final AbstractDescribedSObjectBase object) {
        if (object == null) {
            return null;
        }

        final SObjectDescription description = object.description();
        final List<SObjectField> fields = description.getFields();

        return fields.stream().map(SObjectField::getName).collect(Collectors.joining(", ", "SELECT ", " FROM " + description.getName()));
    }

    public static String queryToFetchFilteredFieldsOf(final AbstractDescribedSObjectBase object, final Predicate<SObjectField> filter) {
        if (object == null) {
            return null;
        }

        if (filter == null) {
            return queryToFetchAllFieldsOf(object);
        }

        final SObjectDescription description = object.description();
        final List<SObjectField> fields = description.getFields();

        return fields.stream().filter(filter).map(SObjectField::getName).collect(Collectors.joining(", ", "SELECT ", " FROM " + description.getName()));
    }
}
