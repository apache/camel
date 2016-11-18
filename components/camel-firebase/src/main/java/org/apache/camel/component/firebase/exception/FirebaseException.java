package org.apache.camel.component.firebase.exception;

import com.google.firebase.database.DatabaseError;

/**
 * Used to mark an exception occurred in the Firebase Camel processor.
 */
public class FirebaseException extends RuntimeException {

    private DatabaseError databaseError;

    /**
     * Constructs a new runtime exception with {@code null} as its
     * detail message.  The cause is not initialized, and may subsequently be
     * initialized by a call to {@link #initCause}.
     */
    public FirebaseException() {
    }

    /**
     * Constructs a new runtime exception with the specified detail message.
     * The cause is not initialized, and may subsequently be initialized by a
     * call to {@link #initCause}.
     *
     * @param message the detail message. The detail message is saved for
     *                later retrieval by the {@link #getMessage()} method.
     */
    public FirebaseException(String message) {
        super(message);
    }

    /**
     * Constructs a new runtime exception with the specified detail message and
     * cause.  <p>Note that the detail message associated with
     * {@code cause} is <i>not</i> automatically incorporated in
     * this runtime exception's detail message.
     *
     * @param message the detail message (which is saved for later retrieval
     *                by the {@link #getMessage()} method).
     * @param cause   the cause (which is saved for later retrieval by the
     *                {@link #getCause()} method).  (A <tt>null</tt> value is
     *                permitted, and indicates that the cause is nonexistent or
     *                unknown.)
     * @since 1.4
     */
    public FirebaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public void setDatabaseError(DatabaseError databaseError) {
        this.databaseError = databaseError;
    }

    public DatabaseError getDatabaseError() {
        return databaseError;
    }
}
