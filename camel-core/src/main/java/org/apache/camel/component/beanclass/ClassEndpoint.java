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
package org.apache.camel.component.beanclass;

import org.apache.camel.Component;
import org.apache.camel.component.bean.BeanEndpoint;
import org.apache.camel.spi.UriEndpoint;

/**
 * The <a href="http://camel.apache.org/class.html">Class Component</a> is for invoking Java Classes (Java beans) from Camel.
 */
@UriEndpoint(firstVersion = "2.4.0", scheme = "class", title = "Class", syntax = "class:beanName", producerOnly = true, label = "core,java")
public class ClassEndpoint extends BeanEndpoint {

    public ClassEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

}
