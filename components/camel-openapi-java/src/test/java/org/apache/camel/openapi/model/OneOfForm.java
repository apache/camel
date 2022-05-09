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
package org.apache.camel.openapi.model;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(oneOf = { XOfFormA.class, XOfFormB.class },
        discriminatorProperty = "code",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "a-123", schema = XOfFormA.class),
                @DiscriminatorMapping(value = "b-456", schema = XOfFormB.class) })
public interface OneOfForm {
    // The discriminator explicitly declares which property you can inspect to determine the object type.
    // The discriminator must apply to the same level of the schema it is declared in (common mistake when using nested objects).
}
