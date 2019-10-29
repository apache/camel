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
import org.apache.camel.component.nitrite.NitriteConstants;
import org.apache.camel.component.nitrite.NitriteEndpoint;
import org.apache.camel.component.nitrite.operation.RepositoryOperation;
import org.dizitart.no2.RemoveOptions;
import org.dizitart.no2.objects.ObjectFilter;
import org.dizitart.no2.objects.ObjectRepository;

/**
 * Remove objects in ObjectRepository matched by ObjectFilter
 */
public class RemoveRepositoryOperation extends AbstractNitriteOperation implements RepositoryOperation {

    private ObjectFilter filter;
    private RemoveOptions removeOptions;

    public RemoveRepositoryOperation(ObjectFilter filter) {
        this.filter = filter;
    }

    public RemoveRepositoryOperation(ObjectFilter filter, RemoveOptions removeOptions) {
        this.filter = filter;
        this.removeOptions = removeOptions;
    }

    @Override
    protected void execute(Exchange exchange, NitriteEndpoint endpoint) throws Exception {
        ObjectRepository repository = (ObjectRepository) endpoint.getNitriteCollection();
        if (filter != null && removeOptions != null) {
            exchange.getMessage().setHeader(NitriteConstants.WRITE_RESULT, repository.remove(filter, removeOptions));
        } else {
            exchange.getMessage().setHeader(NitriteConstants.WRITE_RESULT, repository.remove(filter));
        }
    }
}
