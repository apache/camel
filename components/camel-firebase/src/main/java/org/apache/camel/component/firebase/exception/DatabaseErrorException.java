package org.apache.camel.component.firebase.exception;

import com.google.firebase.database.DatabaseError;

/**
 * In case Firebase throws a database error, this object wraps a Throwable around the original object.
 */
public class DatabaseErrorException extends RuntimeException {

    private final DatabaseError databaseError;

    public DatabaseErrorException(DatabaseError databaseError) {
        super();
        this.databaseError = databaseError;
    }
}
