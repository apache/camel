package org.apache.camel.component.cmis;

public enum CamelCMISActions
{
    CREATE
            { public String getMethodName() { return "createNode"; }},
    DELETE_DOCUMENT
            { public String getMethodName() { return "deleteDocument"; }},
    DELETE_FOLDER
            { public String getMethodName() { return "deleteFolder"; }},
    MOVE_DOCUMENT
            { public String getMethodName() { return "moveDocument"; }},
    MOVE_FOLDER
            { public String getMethodName() { return "moveFolder"; }},
    COPY_DOCUMENT
            { public String getMethodName() { return "copyDocument"; }},
    COPY_FOLDER
            { public String getMethodName() { return "copyFolder"; }},
    RENAME_DOCUMENT
            { public String getMethodName() { return "renameDocument"; }},
    RENAME_FOLDER
            { public String getMethodName() { return "renameFolder"; }},
    CHECK_IN
            { public String getMethodName() { return "checkIn"; }},
    CHECK_OUT
            { public String getMethodName() { return "checkOut"; }},
    CANCEL_CHECK_OUT
            { public String getMethodName() { return "cancelCheckOut"; }};

    public abstract String getMethodName();
}
