package org.apache.camel.component.firebase.data;

/**
 * Operations associated with the messages.
 */
public enum Operation {

    CHILD_ADD,
    CHILD_CHANGED,
    CHILD_REMOVED,
    CHILD_MOVED,
    CANCELLED
}
