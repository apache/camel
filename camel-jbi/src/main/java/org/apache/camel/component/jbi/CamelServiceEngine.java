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
package org.apache.camel.component.jbi;

import org.apache.camel.builder.RouteBuilder;
import org.apache.servicemix.common.BaseComponent;
import org.apache.servicemix.common.BaseServiceUnitManager;
import org.apache.servicemix.common.Deployer;

/**
 * Deploys the camel endpoints within JBI
 * @version $Revision: 426415 $
 */
public class CamelServiceEngine extends BaseComponent {
    
    private RouteBuilder[] builders;

    /* (non-Javadoc)
     * @see org.servicemix.common.BaseComponent#createServiceUnitManager()
     */
    public BaseServiceUnitManager createServiceUnitManager() {
        Deployer[] deployers = new Deployer[] { new CamelContainerDeployer(this,builders) };
        return new BaseServiceUnitManager(this, deployers);
    }

    
    /**
     * @return the builders
     */
    public RouteBuilder[] getBuilders(){
        return this.builders;
    }

    
    /**
     * @param builders the builders to set
     */
    public void setBuilders(RouteBuilder[] builders){
        this.builders=builders;
    }

}
