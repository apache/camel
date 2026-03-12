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
package org.apache.camel.component.leveldb.serializer.jackson;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

public class BodySerializer extends StdSerializer<Object> {
    BodySerializer() {
        super(Object.class);
    }

    @Override
    public void serialize(Object object, JsonGenerator gen, SerializationContext provider) {
        if (object == null) {
            return;
        }

        Package p = object.getClass().getPackage();
        if (p == null || p.getName().equals("java.lang") || p.getName().equals("java.util")) {
            gen.writePOJO(object);
        } else {
            gen.writeStartObject();
            gen.writeName("clazz");
            gen.writePOJO(object.getClass());
            gen.writeName("data");
            gen.writePOJO(object);
            gen.writeEndObject();
        }
    }
}
