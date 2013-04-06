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
package org.apache.camel.impl;

import org.apache.camel.spi.CamelContextNameStrategy;
import org.apache.camel.util.ObjectHelper;

/**
 * Strategy to used an explicit (fixed) name for {@link org.apache.camel.CamelContext}.
 *
 * @version 
 */
public class ExplicitCamelContextNameStrategy implements CamelContextNameStrategy {

    private final String name;

    public ExplicitCamelContextNameStrategy(String name) {
        ObjectHelper.notEmpty(name, "CamelContext name ");
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getNextName() {
        return name;
    }

    @Override
    public boolean isFixedName() {
        return true;
    }
}
