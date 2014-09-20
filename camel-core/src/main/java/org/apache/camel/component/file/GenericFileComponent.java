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
package org.apache.camel.component.file;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * Base class file component. To be extended.
 */
public abstract class GenericFileComponent<T> extends UriEndpointComponent {

    protected Logger log = LoggerFactory.getLogger(getClass());

    public GenericFileComponent() {
        super(GenericFileEndpoint.class);
    }

    public GenericFileComponent(CamelContext context) {
        super(context, GenericFileEndpoint.class);
    }

    protected GenericFileEndpoint<T> createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        // create the correct endpoint based on the protocol
        final GenericFileEndpoint<T> endpoint;

        // call to subclasses to build their custom version of a GenericFileEndpoint
        endpoint = buildFileEndpoint(uri, remaining, parameters);

        // sort by using file language
        String sortBy = getAndRemoveParameter(parameters, "sortBy", String.class);
        if (isNotEmpty(sortBy) && !EndpointHelper.isReferenceParameter(sortBy)) {
            // we support nested sort groups so they should be chained
            String[] groups = sortBy.split(";");
            Iterator<String> it = CastUtils.cast(ObjectHelper.createIterator(groups));
            Comparator<Exchange> comparator = createSortByComparator(it);
            endpoint.setSortBy(comparator);
        }
        setProperties(endpoint.getConfiguration(), parameters);
        setProperties(endpoint, parameters);

        afterPropertiesSet(endpoint);

        return endpoint;
    }

    /**
     * A factory method for derived file components to create the endpoint
     *
     * @param uri the full URI of the endpoint
     * @param remaining the remaining part of the URI without the query
     *                parameters or component prefix
     * @param parameters the optional parameters passed in
     * @return a newly created endpoint or null if the endpoint cannot be
     *         created based on the inputs
     * @throws Exception can be thrown
     */
    protected abstract GenericFileEndpoint<T> buildFileEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception;

    /**
     * A factory method for derived file components to perform validation of properties
     *
     * @param endpoint the endpoint
     * @throws Exception can be thrown in case of validation errors
     */
    protected abstract void afterPropertiesSet(GenericFileEndpoint<T> endpoint) throws Exception;

    /**
     * Helper to create a sort comparator
     *
     * @param it iterator
     * @return Comparator<Exchange>
     */
    private Comparator<Exchange> createSortByComparator(Iterator<String> it) {
        if (!it.hasNext()) {
            return null;
        }

        String group = it.next();

        boolean reverse = group.startsWith("reverse:");
        String reminder = reverse ? ifStartsWithReturnRemainder("reverse:", group) : group;

        boolean ignoreCase = reminder.startsWith("ignoreCase:");
        reminder = ignoreCase ? ifStartsWithReturnRemainder("ignoreCase:", reminder) : reminder;

        ObjectHelper.notEmpty(reminder, "sortBy expression", this);

        // recursive add nested sorters
        return GenericFileDefaultSorter.sortByFileLanguage(getCamelContext(), 
            reminder, reverse, ignoreCase, createSortByComparator(it));
    }
}
