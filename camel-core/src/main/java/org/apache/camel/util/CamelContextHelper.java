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
package org.apache.camel.util;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.NoSuchEndpointException;

/**
 * A number of helper methods
 * 
 * @version $Revision: $
 */
public class CamelContextHelper {
    
    /**
     * Utility classes should not have a public constructor.
     */
    private CamelContextHelper() {        
    }

    /**
     * Returns the mandatory endpoint for the given URI or the
     * {@link org.apache.camel.NoSuchEndpointException} is thrown
     * 
     * @param camelContext
     * @param uri
     * @return
     */
    public static Endpoint getMandatoryEndpoint(CamelContext camelContext, String uri)
        throws NoSuchEndpointException {
        Endpoint endpoint = camelContext.getEndpoint(uri);
        if (endpoint == null) {
            throw new NoSuchEndpointException(uri);
        } else {
            return endpoint;
        }

    }
}
