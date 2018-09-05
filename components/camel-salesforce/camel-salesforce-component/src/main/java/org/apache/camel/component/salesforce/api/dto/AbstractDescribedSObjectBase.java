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
package org.apache.camel.component.salesforce.api.dto;

/**
 * Subclass of {@link AbstractSObjectBase} that contains additional metadata about SObject. The
 * {@code camel-salesforce-maven-plugin} generates Data Transfer Objects (DTO) by subclassing this class and
 * implementing the {@link AbstractDescribedSObjectBase#description()} method from the metadata received from
 * Salesforce. Note that there are no guarantees about all fields in the {@link SObjectDescription} being set. This is
 * to prevent unnecessary memory usage, and to prevent running into Java method length limit.
 */
public abstract class AbstractDescribedSObjectBase extends AbstractSObjectBase {

    /**
     * Additional metadata about this SObject. There are no guarantees that all of the fields of
     * {@link SObjectDescription} will be set.
     *
     * @return metadata description of this SObject
     */
    public abstract SObjectDescription description();

    @SuppressWarnings("boxing")
    protected static SObjectField createField(String name, String label, String type, String soapType, int length,
        boolean unique, boolean nillable, boolean nameField, boolean externalId, boolean custom, boolean caseSensitive,
        boolean idLookup) {
        final SObjectField field = new SObjectField();

        field.setName(name);
        field.setLabel(label);
        field.setType(type);
        field.setSoapType(soapType);
        field.setLength(length);
        field.setUnique(unique);
        field.setNillable(nillable);
        field.setNameField(nameField);
        field.setExternalId(externalId);
        field.setCustom(custom);
        field.setCaseSensitive(caseSensitive);
        field.setIdLookup(idLookup);

        return field;
    }
}
