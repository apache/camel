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
package org.apache.camel.maven.packaging.endpoint;

import org.apache.camel.spi.Metadata;

/**
 * Component options of type boolean and java.lang.Boolean, which the generator reads from the setters.
 */
public class SomeComponentWithBooleanOptions {

    @Metadata(description = "A java.lang.Boolean option without default value")
    private Boolean wrapperWithoutDefault;
    @Metadata(description = "A java.lang.Boolean option with the default value true", defaultValue = "true")
    private Boolean wrapperTrue;
    @Metadata(description = "A boolean option without default value")
    private boolean primitiveWithoutDefault;

    public void setWrapperWithoutDefault(Boolean wrapperWithoutDefault) {
        this.wrapperWithoutDefault = wrapperWithoutDefault;
    }

    public void setWrapperTrue(Boolean wrapperTrue) {
        this.wrapperTrue = wrapperTrue;
    }

    public void setPrimitiveWithoutDefault(boolean primitiveWithoutDefault) {
        this.primitiveWithoutDefault = primitiveWithoutDefault;
    }
}
