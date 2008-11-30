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

import java.io.File;
import java.io.FileFilter;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.processor.idempotent.MessageIdRepository;
import org.apache.camel.util.ObjectHelper;

/**
 * The <a href="http://activemq.apache.org/camel/file.html">File Component</a>
 * for working with file systems
 *
 * @version $Revision$
 */
public class FileComponent extends DefaultComponent {

    /**
     * Header key holding the value: the fixed filename to use for producing files.
     */
    public static final String HEADER_FILE_NAME = "org.apache.camel.file.name";

    /**
     * Header key holding the value: absolute filepath for the actual file produced (by file producer).
     * Value is set automatically by Camel
     */
    public static final String HEADER_FILE_NAME_PRODUCED = "org.apache.camel.file.name.produced";

    /**
     * Header key holding the value: current index of total in the batch being consumed
     */
    public static final String HEADER_FILE_BATCH_INDEX = "org.apache.camel.file.index";

    /**
     * Header key holding the value: total in the batch being consumed
     */
    public static final String HEADER_FILE_BATCH_TOTAL = "org.apache.camel.file.total";

    public FileComponent() {
    }

    public FileComponent(CamelContext context) {
        super(context);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        File file = new File(remaining);
        FileEndpoint result = new FileEndpoint(file, uri, this);

        // lookup idempotent repository in registry if provided
        String ref = getAndRemoveParameter(parameters, "idempotentRepositoryRef", String.class);
        if (ref != null) {
            MessageIdRepository repository = mandatoryLookup(ref, MessageIdRepository.class);
            result.setIdempotentRepository(repository);
        }

        // lookup file filter in registry if provided
        ref = getAndRemoveParameter(parameters, "fileFilterRef", String.class);
        if (ref != null) {
            FileFilter filter = mandatoryLookup(ref, FileFilter.class);
            result.setFilter(filter);
        }

        // lookup sorter in registry if provided
        ref = getAndRemoveParameter(parameters, "sorterRef", String.class);
        if (ref != null) {
            Comparator<File> sorter = mandatoryLookup(ref, Comparator.class);
            result.setFileSorter(sorter);
        }

        // sort by using file language 
        String sortBy = getAndRemoveParameter(parameters, "sortBy", String.class);
        if (sortBy != null) {
            // we support nested sort groups so they should be chained
            String[] groups = sortBy.split(";");
            Iterator<String> it = ObjectHelper.createIterator(groups);
            Comparator<FileExchange> comparator = createSortByComparator(it);
            result.setExchangeSorter(comparator);
        }

        setProperties(result, parameters);
        return result;
    }

    private Comparator<FileExchange> createSortByComparator(Iterator<String> it) {
        if (!it.hasNext()) {
            return null;
        }

        String group = it.next();
        boolean reverse = group.startsWith("reverse:");
        String reminder = reverse ? ifStartsWithReturnRemainder("reverse:", group) : group;
        ObjectHelper.notNull(reminder, "sortBy option does not contain the expression");

        // recursive add nested sorters
        return DefaultFileSorter.sortByFileLanguage(reminder, reverse, createSortByComparator(it));
    }

}
