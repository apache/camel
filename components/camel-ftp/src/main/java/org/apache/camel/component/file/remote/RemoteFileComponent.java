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
package org.apache.camel.component.file.remote;

import java.net.URI;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;

/**
 * Remote file component.
 */
public class RemoteFileComponent extends DefaultComponent {

    public RemoteFileComponent() {
    }

    public RemoteFileComponent(CamelContext context) {
        super(context);
    }

    protected RemoteFileEndpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        // get the uri part before the options as they can be non URI valid such as the expression using $ chars
        if (uri.indexOf("?") != -1) {
            uri = uri.substring(0, uri.indexOf("?"));
        }

        // lets make sure we create a new configuration as each endpoint can customize its own version
        RemoteFileConfiguration config = new RemoteFileConfiguration(new URI(uri));

        // create the correct endpoint based on the protocol
        final RemoteFileEndpoint endpoint;
        if ("ftp".equals(config.getProtocol())) {
            RemoteFileOperations operations = new FtpRemoteFileOperations();
            endpoint = new RemoteFileEndpoint(uri, this, operations, config);
        } else if ("sftp".equals(config.getProtocol())) {
            RemoteFileOperations operations = new SftpRemoteFileOperations();
            endpoint = new RemoteFileEndpoint(uri, this, operations, config);
        } else {
            throw new IllegalArgumentException("Unsupported protocol: " + config.getProtocol());
        }

        // sort by using file language
        String sortBy = getAndRemoveParameter(parameters, "sortBy", String.class);
        if (isNotEmpty(sortBy) && !isReferenceParameter(sortBy)) {
            // we support nested sort groups so they should be chained
            String[] groups = sortBy.split(";");
            Iterator<String> it = ObjectHelper.createIterator(groups);
            Comparator<RemoteFileExchange> comparator = createSortByComparator(it);
            endpoint.setSortBy(comparator);
        }

        setProperties(endpoint.getConfiguration(), parameters);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    private Comparator<RemoteFileExchange> createSortByComparator(Iterator<String> it) {
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
        return DefaultRemoteFileSorter.sortByFileLanguage(reminder, reverse, ignoreCase, createSortByComparator(it));
    }

}
