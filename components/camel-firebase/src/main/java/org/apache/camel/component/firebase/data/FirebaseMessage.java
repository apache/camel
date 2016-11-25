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
package org.apache.camel.component.firebase.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;

/**
 * Contains a message sent by Firebase.
 */
public final class FirebaseMessage {

    private final Operation operation;

    private final DataSnapshot dataSnapshot;

    private final String previousChildName;

    private final DatabaseError databaseError;

    private FirebaseMessage(Builder builder) {
        this.operation = builder.operation;
        this.dataSnapshot = builder.dataSnapshot;
        this.previousChildName = builder.previousChildName;
        this.databaseError = builder.databaseError;
    }

    public Operation getOperation() {
        return operation;
    }

    public DataSnapshot getDataSnapshot() {
        return dataSnapshot;
    }

    public String getPreviousChildName() {
        return previousChildName;
    }

    public DatabaseError getDatabaseError() {
        return databaseError;
    }

    @Override
    public String toString() {
        return "FirebaseMessage{"
                + "operation=" + operation
                + ", dataSnapshot=" + dataSnapshot
                + ", previousChildName='" + previousChildName + '\''
                + ", databaseError=" + databaseError + '}';
    }

    public static class Builder {
        private final Operation operation;

        private DataSnapshot dataSnapshot;

        private String previousChildName;

        private DatabaseError databaseError;

        public Builder(Operation operation) {
            this.operation = operation;
        }

        public Builder(Operation operation, DataSnapshot dataSnapshot) {
            this.operation = operation;
            this.dataSnapshot = dataSnapshot;
        }

        public Builder setPreviousChildName(String previousChildName) {
            this.previousChildName = previousChildName;
            return this;
        }

        public Builder setDataSnapshot(DataSnapshot dataSnapshot) {
            this.dataSnapshot = dataSnapshot;
            return this;
        }

        public Builder setDatabaseError(DatabaseError databaseError) {
            this.databaseError = databaseError;
            return this;
        }

        public FirebaseMessage build() {
            return new FirebaseMessage(this);
        }
    }
}
