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
package org.apache.camel.component.infinispan.remote.protostream;

import org.apache.camel.support.DefaultExchangeHolder;
import org.infinispan.protostream.annotations.ProtoAdapter;
import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;

/**
 * Custom {@link ProtoAdapter} to serialize / deserialize {@link DefaultExchangeHolder} to / from byte[].
 */
@ProtoAdapter(DefaultExchangeHolder.class)
final class DefaultExchangeHolderProtoAdapter {
    @ProtoFactory
    public DefaultExchangeHolder create(byte[] serializedExchangeHolderBytes) {
        return DefaultExchangeHolderUtils.deserialize(serializedExchangeHolderBytes);
    }

    @ProtoField(number = 1, required = true)
    public byte[] serializedExchangeHolderBytes(DefaultExchangeHolder defaultExchangeHolder) {
        return DefaultExchangeHolderUtils.serialize(defaultExchangeHolder);
    }
}
