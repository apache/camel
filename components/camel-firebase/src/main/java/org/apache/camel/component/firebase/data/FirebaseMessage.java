package org.apache.camel.component.firebase.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;

/**
 * Contains a message sent by Firebase.
 */
public class FirebaseMessage {

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
        return "FirebaseMessage{" + "operation=" + operation +
                ", dataSnapshot=" + dataSnapshot +
                ", previousChildName='" + previousChildName + '\'' +
                ", databaseError=" + databaseError +
                '}';
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
