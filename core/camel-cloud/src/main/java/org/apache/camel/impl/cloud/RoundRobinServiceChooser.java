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

import org.apache.camel.cloud.ServiceChooser;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.util.ObjectHelper;

public class RoundRobinServiceChooser implements ServiceChooser {
    private int counter = -1;

    @Override
    public synchronized ServiceDefinition choose(List<ServiceDefinition> definitions) {
        // Fail if the service definition list is null or empty
        if (ObjectHelper.isEmpty(definitions)) {
            throw new IllegalArgumentException("The ServiceDefinition list should not be empty");
        }

        int size = definitions.size();
        if (size == 1 || ++counter >= size) {
            counter = 0;
        }

        return definitions.get(counter);
    }

    @Override
    public String toString() {
        return "RoundRobinServiceChooser";
    }
}
