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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * DefaultExchangeHolder uses type Object for inBody and outBody. Which caused during deserialization, that Jackson
 * creates map of values instead of the required instance. To solve this, parameter clazz is sarialized to make correct
 * deserialization possible. (types from different packages than java.lang and java.util, are stored)
 */
public abstract class HolderBodyMixin {
    @JsonSerialize(using = BodySerializer.class)
    @JsonDeserialize(using = BodyDeserializer.class)
    private Object inBody;

    @JsonSerialize(using = BodySerializer.class)
    @JsonDeserialize(using = BodyDeserializer.class)
    private Object outBody;
}
