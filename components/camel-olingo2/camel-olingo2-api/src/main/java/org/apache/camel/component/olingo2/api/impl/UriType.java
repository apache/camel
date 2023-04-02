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
package org.apache.camel.component.olingo2.api.impl;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Copied from Olingo2 core package.
 */
public enum UriType {
    /**
     * Service document
     */
    URI0(SystemQueryOption.$format),
    /**
     * Entity set
     */
    URI1(SystemQueryOption.$format, SystemQueryOption.$filter, SystemQueryOption.$inlinecount, SystemQueryOption.$orderby,
         SystemQueryOption.$skiptoken, SystemQueryOption.$skip,
         SystemQueryOption.$top, SystemQueryOption.$expand, SystemQueryOption.$select),
    /**
     * Entity set with key predicate
     */
    URI2(SystemQueryOption.$format, SystemQueryOption.$filter, SystemQueryOption.$expand, SystemQueryOption.$select),
    /**
     * Complex property of an entity
     */
    URI3(SystemQueryOption.$format),
    /**
     * Simple property of a complex property of an entity
     */
    URI4(SystemQueryOption.$format),
    /**
     * Simple property of an entity
     */
    URI5(SystemQueryOption.$format),
    /**
     * Navigation property of an entity with target multiplicity '1' or '0..1'
     */
    URI6A(SystemQueryOption.$format, SystemQueryOption.$filter, SystemQueryOption.$expand, SystemQueryOption.$select),
    /**
     * Navigation property of an entity with target multiplicity '*'
     */
    URI6B(SystemQueryOption.$format, SystemQueryOption.$filter, SystemQueryOption.$inlinecount, SystemQueryOption.$orderby,
          SystemQueryOption.$skiptoken, SystemQueryOption.$skip,
          SystemQueryOption.$top, SystemQueryOption.$expand, SystemQueryOption.$select),
    /**
     * Link to a single entity
     */
    URI7A(SystemQueryOption.$format, SystemQueryOption.$filter),
    /**
     * Link to multiple entities
     */
    URI7B(SystemQueryOption.$format, SystemQueryOption.$filter, SystemQueryOption.$inlinecount, SystemQueryOption.$orderby,
          SystemQueryOption.$skiptoken, SystemQueryOption.$skip,
          SystemQueryOption.$top),
    /**
     * Metadata document
     */
    URI8(),
    /**
     * Batch request
     */
    URI9(),
    /**
     * Function import returning a single instance of an entity type
     */
    URI10(SystemQueryOption.$format),
    /**
     * Function import returning a collection of complex-type instances
     */
    URI11(SystemQueryOption.$format),
    /**
     * Function import returning a single instance of a complex type
     */
    URI12(SystemQueryOption.$format),
    /**
     * Function import returning a collection of primitive-type instances
     */
    URI13(SystemQueryOption.$format),
    /**
     * Function import returning a single instance of a primitive type
     */
    URI14(SystemQueryOption.$format),
    /**
     * Count of an entity set
     */
    URI15(SystemQueryOption.$filter, SystemQueryOption.$orderby, SystemQueryOption.$skip, SystemQueryOption.$top),
    /**
     * Count of a single entity
     */
    URI16(SystemQueryOption.$filter),
    /**
     * Media resource of an entity
     */
    URI17(SystemQueryOption.$format, SystemQueryOption.$filter),
    /**
     * Count of link to a single entity
     */
    URI50A(SystemQueryOption.$filter),
    /**
     * Count of links to multiple entities
     */
    URI50B(SystemQueryOption.$filter, SystemQueryOption.$orderby, SystemQueryOption.$skip, SystemQueryOption.$top);

    private final ArrayList<SystemQueryOption> whiteList = new ArrayList<>();

    UriType(final SystemQueryOption... compatibleQueryOptions) {
        whiteList.addAll(Arrays.asList(compatibleQueryOptions));
    }

    public boolean isCompatible(final SystemQueryOption queryOption) {
        return whiteList.contains(queryOption);
    }

}
