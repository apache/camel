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
package org.apache.camel.impl.engine;

import org.apache.camel.ValueHolder;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.util.StringHelper;

/**
 * Key used in {@link org.apache.camel.spi.TransformerRegistry} in
 * {@link org.apache.camel.impl.engine.AbstractCamelContext}, to ensure a consistent lookup.
 */
public final class TransformerKey extends ValueHolder<String> {

    private final DataType from;
    private final DataType to;

    public TransformerKey(String toType) {
        this(DataType.ANY, new DataType(toType));
        StringHelper.notEmpty(toType, "toType");
    }

    public TransformerKey(DataType to) {
        this(DataType.ANY, to);
    }

    public TransformerKey(DataType from, DataType to) {
        super(createKeyString(from, to));
        this.from = from;
        this.to = to;
    }

    /**
     * Create the string that represents this transformer key. Either uses both full names of from/to data types in
     * combination or only uses the toType data type full name in case fromType is not specified.
     *
     * @param  from
     * @param  to
     * @return
     */
    private static String createKeyString(DataType from, DataType to) {
        if (DataType.isAnyType(from)) {
            return to.getFullName();
        }

        return from.getFullName() + "/" + to.getFullName();
    }

    /**
     * Create the transformer key for the given transformer either using the transformer name or it's specified from/to
     * data type name.
     *
     * @param  answer
     * @return
     */
    public static TransformerKey createFrom(Transformer answer) {
        if (!DataType.isAnyType(answer.getFrom()) && !DataType.isAnyType(answer.getTo())) {
            return new TransformerKey(answer.getFrom(), answer.getTo());
        }

        return new TransformerKey(answer.getName());
    }

    public DataType getFrom() {
        return from;
    }

    public DataType getTo() {
        return to;
    }

    @Override
    public String toString() {
        return get();
    }

}
