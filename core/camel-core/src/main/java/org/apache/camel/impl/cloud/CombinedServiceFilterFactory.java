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
package org.apache.camel.impl.cloud;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.cloud.ServiceFilter;
import org.apache.camel.cloud.ServiceFilterFactory;
import org.apache.camel.spi.annotations.CloudServiceFactory;
import org.apache.camel.util.ObjectHelper;

@CloudServiceFactory("combined-service-filter")
public class CombinedServiceFilterFactory implements ServiceFilterFactory {
    private List<ServiceFilter> serviceFilterList;

    public CombinedServiceFilterFactory() {
    }

    // *************************************************************************
    // Properties
    // *************************************************************************

    public List<ServiceFilter> getServiceFilterList() {
        return serviceFilterList;
    }

    public void setServiceFilterList(List<ServiceFilter> serviceFilterList) {
        this.serviceFilterList = serviceFilterList;
    }

    // *************************************************************************
    // Factory
    // *************************************************************************

    @Override
    public ServiceFilter newInstance(CamelContext camelContext) throws Exception {
        ObjectHelper.notNull(serviceFilterList, "ServiceFilter list");

        return new CombinedServiceFilter(serviceFilterList);
    }
}
