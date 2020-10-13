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
package org.apache.camel.language.simple.types;

/**
 * Holder for a token, with associated type and position in the input.
 */
public final class SimpleToken {

    private final SimpleTokenType type;
    private final int index;
    private final int length;

    public SimpleToken(SimpleTokenType type, int index) {
        this(type, index, type.getValue() != null ? type.getValue().length() : 0);
    }

    public SimpleToken(SimpleTokenType type, int index, int length) {
        this.type = type;
        this.index = index;
        this.length = length;
    }

    public SimpleTokenType getType() {
        return type;
    }

    public int getIndex() {
        return index;
    }

    public String getText() {
        return type.getValue();
    }

    public int getLength() {
        return length;
    }

    @Override
    public String toString() {
        return type.toString();
    }
}
