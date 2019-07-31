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
package org.apache.camel.component.mybatis;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

@Component("mybatis-bean")
public class MyBatisBeanComponent extends MyBatisComponent {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String beanName = StringHelper.before(remaining, ":");
        String methodName = StringHelper.after(remaining, ":");

        if (ObjectHelper.isEmpty(beanName)) {
            throw new IllegalArgumentException("The option beanName must be provided when creating endpoint: " + uri);
        }
        if (ObjectHelper.isEmpty(methodName)) {
            throw new IllegalArgumentException("The option methodName must be provided when creating endpoint: " + uri);
        }

        MyBatisBeanEndpoint answer = new MyBatisBeanEndpoint(uri, this, beanName, methodName);
        setProperties(answer, parameters);
        return answer;
    }

}
