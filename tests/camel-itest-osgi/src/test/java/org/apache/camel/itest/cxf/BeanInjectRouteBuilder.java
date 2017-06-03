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
package org.apache.camel.itest.cxf;

import org.apache.camel.BeanInject;
import org.apache.camel.builder.RouteBuilder;

public class BeanInjectRouteBuilder extends RouteBuilder {

    @BeanInject
    private SimpleBean simpleBean;

    @Override
    public void configure() throws Exception {
        from("cxf:bean:routerEndpoint")
            .bean(simpleBean) // does nothing
            .transform(simple("scheme: ${headers.CamelCxfMessage.get(HTTP.REQUEST).scheme}"
                + ", x-forwarded-proto: ${headers.CamelCxfMessage.get(HTTP.REQUEST).getHeader(X-Forwarded-Proto)}"));
    }

}
