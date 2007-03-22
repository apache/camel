/*
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

import javax.jbi.management.DeploymentException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.servicemix.common.Deployer;
import org.apache.servicemix.common.ServiceMixComponent;
import org.apache.servicemix.common.ServiceUnit;

/**
 * Deploys service units
 * @version $Revision: 426415 $
 */
public class CamelContainerDeployer implements Deployer {

    private RouteBuilder[] builders;
    ServiceUnit serviceUnit;
    CamelContainerDeployer(ServiceMixComponent component,RouteBuilder[] builders){
        this.serviceUnit = new ServiceUnit(component);
        this.builders = builders;
        //need to wire-up here
    }
    /**
     * @param serviceUnitName
     * @param serviceUnitRootPath
     * @return
     * @see org.apache.servicemix.common.Deployer#canDeploy(java.lang.String, java.lang.String)
     */
    public boolean canDeploy(String serviceUnitName,String serviceUnitRootPath){
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * @param serviceUnitName
     * @param serviceUnitRootPath
     * @return
     * @throws DeploymentException
     * @see org.apache.servicemix.common.Deployer#deploy(java.lang.String, java.lang.String)
     */
    public ServiceUnit deploy(String serviceUnitName,String serviceUnitRootPath) throws DeploymentException{
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @param su
     * @throws DeploymentException
     * @see org.apache.servicemix.common.Deployer#undeploy(org.apache.servicemix.common.ServiceUnit)
     */
    public void undeploy(ServiceUnit su) throws DeploymentException{
        // TODO Auto-generated method stub
        
    }

    

}
