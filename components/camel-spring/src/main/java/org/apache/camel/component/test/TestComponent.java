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
package org.apache.camel.component.test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

/**
 * Test Component.
 *
 * @see org.apache.camel.component.test.TestEndpoint
 *
 * @version $Revision$
 */
public class TestComponent extends DefaultComponent<Exchange> {

    public Endpoint<Exchange> createEndpoint(String uri) throws Exception {
        // lets not use the normal parameter handling so that all parameters are sent to the nested endpoint

        ObjectHelper.notNull(getCamelContext(), "camelContext");
        URI u = new URI(UnsafeUriCharactersEncoder.encode(uri));
        String path = u.getSchemeSpecificPart();

        return createEndpoint(uri, path, new HashMap());
    }

    @Override
    protected Endpoint<Exchange> createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        Endpoint endpoint = CamelContextHelper.getMandatoryEndpoint(getCamelContext(), remaining);
        return new TestEndpoint(uri, this, endpoint);
    }

}