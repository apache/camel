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
package org.apache.camel.component.nitrite.operation.repository;

import org.apache.camel.Exchange;
import org.apache.camel.component.nitrite.AbstractNitriteOperation;
import org.apache.camel.component.nitrite.NitriteEndpoint;
import org.apache.camel.component.nitrite.operation.RepositoryOperation;
import org.dizitart.no2.FindOptions;
import org.dizitart.no2.objects.ObjectFilter;
import org.dizitart.no2.objects.ObjectRepository;

/**
 * Find objects in ObjectRepository by ObjectFilter. If not specified, returns all objects in repository
 */
public class FindRepositoryOperation extends AbstractNitriteOperation implements RepositoryOperation {

    private ObjectFilter objectFilter;
    private FindOptions findOptions;

    public FindRepositoryOperation() {
    }

    public FindRepositoryOperation(ObjectFilter objectFilter) {
        this.objectFilter = objectFilter;
    }

    public FindRepositoryOperation(ObjectFilter objectFilter, FindOptions findOptions) {
        this.objectFilter = objectFilter;
        this.findOptions = findOptions;
    }

    @Override
    protected void execute(Exchange exchange, NitriteEndpoint endpoint) throws Exception {
        ObjectRepository repository = (ObjectRepository) endpoint.getNitriteCollection();
        if (objectFilter != null && findOptions != null) {
            exchange.getMessage().setBody(repository.find(objectFilter, findOptions));
        } else if (objectFilter != null) {
            exchange.getMessage().setBody(repository.find(objectFilter));
        } else {
            exchange.getMessage().setBody(repository.find());
        }
    }
}
