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
package org.apache.camel.impl.transformer;

import org.apache.camel.ValueHolder;
import org.apache.camel.spi.DataType;
import org.apache.camel.util.StringHelper;

/**
 * Key used in {@link org.apache.camel.spi.TransformerRegistry} in {@link org.apache.camel.impl.engine.AbstractCamelContext},
 * to ensure a consistent lookup.
 */
public final class TransformerKey extends ValueHolder<String> {

    private String scheme;
    private DataType from;
    private DataType to;

    public TransformerKey(String scheme) {
        super(scheme);
        StringHelper.notEmpty(scheme, "scheme");
        this.scheme = scheme;
    }

    public TransformerKey(DataType from, DataType to) {
        super(createKeyString(from, to));
        this.from = from;
        this.to = to;
    }

    private static String createKeyString(DataType from, DataType to) {
        return from + "/" + to;
    }

    public String getScheme() {
        return scheme;
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
