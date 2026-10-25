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
package org.apache.camel.component.beanclass;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.component.bean.BeanEndpoint;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.UriEndpoint;

/**
 * Invoke methods of Java beans specified by class name.
 */
@UriEndpoint(firstVersion = "2.4.0", scheme = "class", title = "Class", syntax = "class:beanName", producerOnly = true,
             remote = false, category = { Category.CORE, Category.SCRIPT })
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The first parameter of the method gets the body, converted to its type: a"
                                                + " String parameter gets a Map or a POJO as its toString(), so marshal it"
                                                + " first"),
           producerOut = @BodyType(value = Carrier.UNKNOWN,
                                   description = "What the method returns, also when it returns null. A void method leaves"
                                                 + " the body unchanged"))
public class ClassEndpoint extends BeanEndpoint {

    public ClassEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

}
