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
package org.apache.camel.component.stub;

import java.util.Map;

import org.apache.camel.component.vm.VmComponent;

/**
 * Allows you to easily stub out a middleware transport by prefixing the URI with "stub:" which is
 * handy for testing out routes, or isolating bits of middleware.
 */
public class StubComponent extends VmComponent {

    public StubComponent() {
    }

    @Override
    protected void validateURI(String uri, String path, Map<String, Object> parameters) {
        // Don't validate so we can stub any URI
    }

    @Override
    protected void validateParameters(String uri, Map<String, Object> parameters, String optionPrefix) {
        // Don't validate so we can stub any URI
    }

}
