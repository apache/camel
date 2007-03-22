/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.apache.camel.component.jbi;

import java.util.List;
import org.apache.servicemix.common.DefaultComponent;

/**
 * Deploys the camel endpoints within JBI
 * 
 * @version $Revision: 426415 $
 */
public class CamelJbiComponent extends DefaultComponent{

    private CamelJbiEndpoint[] endpoints;

    /**
     * @return the endpoints
     */
    public CamelJbiEndpoint[] getEndpoints(){
        return this.endpoints;
    }

    /**
     * @param endpoints the endpoints to set
     */
    public void setEndpoints(CamelJbiEndpoint[] endpoints){
        this.endpoints=endpoints;
    }

    /**
     * @return List of endpoints
     * @see org.apache.servicemix.common.DefaultComponent#getConfiguredEndpoints()
     */
    @Override protected List getConfiguredEndpoints(){
        return asList(getEndpoints());
    }

    /**
     * @return Class[]
     * @see org.apache.servicemix.common.DefaultComponent#getEndpointClasses()
     */
    @Override protected Class[] getEndpointClasses(){
        return new Class[] { CamelJbiEndpoint.class };
    }
}
