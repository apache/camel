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
package org.apache.camel.component.mongodb;

import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.Resumable;
import org.apache.camel.support.resume.OffsetKeys;
import org.apache.camel.support.resume.Offsets;
import org.bson.BsonDocument;

public final class MongoDbResumable implements Resumable {
    private final String addressable;
    private final String resumeToken;

    private MongoDbResumable(String addressable, String resumeToken) {
        this.addressable = addressable;
        this.resumeToken = resumeToken;
    }

    @Override
    public OffsetKey<?> getOffsetKey() {
        return OffsetKeys.unmodifiableOf(addressable);
    }

    @Override
    public Offset<?> getLastOffset() {
        return Offsets.of(resumeToken);
    }

    public static MongoDbResumable of(String addressable, BsonDocument resumeToken) {
        return new MongoDbResumable(addressable, resumeToken.toJson());
    }
}
