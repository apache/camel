/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cxf;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.cxf.Bus;
import org.apache.cxf.bus.CXFBusFactory;

import java.net.URI;
import java.util.Map;
import java.util.Properties;

/**
 * @version $Revision$
 */
public class CxfInvokeComponent extends DefaultComponent<CxfExchange> {
    private Bus bus;

    public CxfInvokeComponent() {
        bus = CXFBusFactory.getDefaultBus();
    }

    public CxfInvokeComponent(CamelContext context) {
        super(context);
        bus = CXFBusFactory.getDefaultBus();
    }

    @Override
    protected Endpoint<CxfExchange> createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        return new CxfInvokeEndpoint(getAddress(remaining), this, getQueryAsProperties(new URI(remaining)));
    }

    /**
     * Read query parameters from uri
     *
     * @param u
     * @return parameter value pairs as properties
     */
    protected Properties getQueryAsProperties(URI u) {
        Properties retval = new Properties();
        if (u.getQuery() != null) {
            String[] parameters = u.getQuery().split("&");
            for (int i = 0; i < parameters.length; i++) {
                String[] s = parameters[i].split("=");
                retval.put(s[0], s[1]);
            }
        }
        return retval;
    }

    /**
     * Remove query from uri
     *
     * @param uri
     * @return substring before  the "?" character
     */
    protected String getAddress(String uri) {
        int index = uri.indexOf("?");
        if (-1 != index) {
            return uri.substring(0, index);
        }
        return uri;
    }

    public Bus getBus() {
        return bus;
    }
}