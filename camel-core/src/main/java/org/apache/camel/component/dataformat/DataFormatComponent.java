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
package org.apache.camel.component.dataformat;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * The <a href="http://camel.apache.org/dataformat-component.html">Data Format Component</a> enables using <a href="https://camel.apache.org/data-format.html">Data Format</a> as a component.
 *
 * @version
 */
public class DataFormatComponent extends UriEndpointComponent {

    public DataFormatComponent() {
        super(DataFormatEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String name = ObjectHelper.before(remaining, ":");

        // try to lookup data format in the registry or create it from resource
        DataFormat df = getCamelContext().resolveDataFormat(name);
        if (df == null) {
            // if not, try to find a factory in the registry
            df = getCamelContext().createDataFormat(name);
        }
        if (df == null) {
            throw new IllegalArgumentException("Cannot find data format with name: " + name);
        }

        String operation = ObjectHelper.after(remaining, ":");
        if (!"marshal".equals(operation) && !"unmarshal".equals(operation)) {
            throw new IllegalArgumentException("Operation must be either marshal or unmarshal, was: " + operation);
        }

        // set reference properties first as they use # syntax that fools the regular properties setter
        EndpointHelper.setReferenceProperties(getCamelContext(), df, parameters);
        EndpointHelper.setProperties(getCamelContext(), df, parameters);

        DataFormatEndpoint endpoint = new DataFormatEndpoint(uri, this, df);
        endpoint.setOperation(operation);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
