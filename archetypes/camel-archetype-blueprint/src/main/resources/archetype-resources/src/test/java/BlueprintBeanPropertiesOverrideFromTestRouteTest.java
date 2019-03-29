## ------------------------------------------------------------------------
## Licensed to the Apache Software Foundation (ASF) under one or more
## contributor license agreements.  See the NOTICE file distributed with
## this work for additional information regarding copyright ownership.
## The ASF licenses this file to You under the Apache License, Version 2.0
## (the "License"); you may not use this file except in compliance with
## the License.  You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software
## distributed under the License is distributed on an "AS IS" BASIS,
## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
## See the License for the specific language governing permissions and
## limitations under the License.
## ------------------------------------------------------------------------
package ${package};

import java.util.Dictionary;

import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;

import org.junit.Test;

public class BlueprintBeanPropertiesOverrideFromTestRouteTest extends CamelBlueprintTestSupport {
	
    @Override
    protected String getBlueprintDescriptor() {
        return "/OSGI-INF/blueprint/blueprint-bean.xml";
    }

    @Override
    protected String useOverridePropertiesWithConfigAdmin(Dictionary props) throws Exception {
        // override / add extra properties
        props.put("greeting", "Hello from test");

        // return the persistence-id to use
        return "HelloBean";
    }

    @Test
    public void testRoute() throws Exception {
        // the route is timer based, so every 5th second a message is send
        // we should then expect at least one message
        getMockEndpoint("mock:result").expectedMinimumMessageCount(1);

        // assert expectations
        assertMockEndpointsSatisfied();
    }

}
