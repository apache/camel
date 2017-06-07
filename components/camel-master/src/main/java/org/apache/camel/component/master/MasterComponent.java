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
package org.apache.camel.component.master;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * The master camel component ensures that only a single endpoint in a cluster is
 * active at any point in time with all other JVMs being hot standbys which wait
 * until the master JVM dies before taking over to provide high availability of
 * a single consumer.
 */
public class MasterComponent extends DefaultComponent {

    public MasterComponent() {
        this(null);
    }

    public MasterComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> params) throws Exception {
        // we are registering a regular endpoint
        String namespace = StringHelper.before(remaining, ":");
        String delegateUri = StringHelper.after(remaining, ":");

        if (ObjectHelper.isEmpty(namespace) || ObjectHelper.isEmpty(delegateUri)) {
            throw new IllegalArgumentException("Wrong uri syntax : master:namespace:uri, got " + remaining);
        }

        // we need to apply the params here
        if (params != null && params.size() > 0) {
            delegateUri = delegateUri + "?" + uri.substring(uri.indexOf('?') + 1);
        }

        return new MasterEndpoint(uri, this, namespace, delegateUri);
    }
}
