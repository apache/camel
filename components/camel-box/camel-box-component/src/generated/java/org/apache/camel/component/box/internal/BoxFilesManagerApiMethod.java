/*
 * Camel ApiMethod Enumeration generated by camel-api-component-maven-plugin
 */
package org.apache.camel.component.box.internal;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.camel.component.box.api.BoxFilesManager;

import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodArg;
import org.apache.camel.support.component.ApiMethodImpl;

import static org.apache.camel.support.component.ApiMethodArg.arg;
import static org.apache.camel.support.component.ApiMethodArg.setter;

/**
 * Camel {@link ApiMethod} Enumeration for org.apache.camel.component.box.api.BoxFilesManager
 */
public enum BoxFilesManagerApiMethod implements ApiMethod {

    CHECK_UPLOAD(
        void.class,
        "checkUpload",
        arg("fileName", String.class),
        arg("parentFolderId", String.class),
        arg("size", Long.class)),

    COPY_FILE(
        com.box.sdk.BoxFile.class,
        "copyFile",
        arg("fileId", String.class),
        arg("destinationFolderId", String.class),
        arg("newName", String.class)),

    CREATE_FILE_METADATA(
        com.box.sdk.Metadata.class,
        "createFileMetadata",
        arg("fileId", String.class),
        arg("metadata", com.box.sdk.Metadata.class),
        arg("typeName", String.class)),

    CREATE_FILE_SHARED_LINK(
        com.box.sdk.BoxSharedLink.class,
        "createFileSharedLink",
        arg("fileId", String.class),
        arg("access", com.box.sdk.BoxSharedLink.Access.class),
        arg("unshareDate", java.util.Date.class),
        arg("permissions", com.box.sdk.BoxSharedLink.Permissions.class)),

    DELETE_FILE(
        void.class,
        "deleteFile",
        arg("fileId", String.class)),

    DELETE_FILE_METADATA(
        void.class,
        "deleteFileMetadata",
        arg("fileId", String.class)),

    DELETE_FILE_VERSION(
        void.class,
        "deleteFileVersion",
        arg("fileId", String.class),
        arg("version", Integer.class)),

    DOWNLOAD_FILE(
        java.io.OutputStream.class,
        "downloadFile",
        arg("fileId", String.class),
        arg("output", java.io.OutputStream.class),
        arg("rangeStart", Long.class),
        arg("rangeEnd", Long.class),
        arg("listener", com.box.sdk.ProgressListener.class)),

    DOWNLOAD_PREVIOUS_FILE_VERSION(
        java.io.OutputStream.class,
        "downloadPreviousFileVersion",
        arg("fileId", String.class),
        arg("version", Integer.class),
        arg("output", java.io.OutputStream.class),
        arg("listener", com.box.sdk.ProgressListener.class)),

    GET_DOWNLOAD_URL(
        java.net.URL.class,
        "getDownloadURL",
        arg("fileId", String.class)),

    GET_FILE_INFO(
        com.box.sdk.BoxFile.Info.class,
        "getFileInfo",
        arg("fileId", String.class),
        arg("fields", new String[0].getClass())),

    GET_FILE_METADATA(
        com.box.sdk.Metadata.class,
        "getFileMetadata",
        arg("fileId", String.class),
        arg("typeName", String.class)),

    GET_FILE_PREVIEW_LINK(
        java.net.URL.class,
        "getFilePreviewLink",
        arg("fileId", String.class)),

    GET_FILE_VERSIONS(
        java.util.Collection.class,
        "getFileVersions",
        arg("fileId", String.class)),

    MOVE_FILE(
        com.box.sdk.BoxFile.class,
        "moveFile",
        arg("fileId", String.class),
        arg("destinationFolderId", String.class),
        arg("newName", String.class)),

    PROMOTE_FILE_VERSION(
        com.box.sdk.BoxFileVersion.class,
        "promoteFileVersion",
        arg("fileId", String.class),
        arg("version", Integer.class)),

    RENAME_FILE(
        com.box.sdk.BoxFile.class,
        "renameFile",
        arg("fileId", String.class),
        arg("newFileName", String.class)),

    UPDATE_FILE_INFO(
        com.box.sdk.BoxFile.class,
        "updateFileInfo",
        arg("fileId", String.class),
        arg("info", com.box.sdk.BoxFile.Info.class)),

    UPDATE_FILE_METADATA(
        com.box.sdk.Metadata.class,
        "updateFileMetadata",
        arg("fileId", String.class),
        arg("metadata", com.box.sdk.Metadata.class)),

    UPLOAD_FILE(
        com.box.sdk.BoxFile.class,
        "uploadFile",
        arg("parentFolderId", String.class),
        arg("content", java.io.InputStream.class),
        arg("fileName", String.class),
        arg("created", java.util.Date.class),
        arg("modified", java.util.Date.class),
        arg("size", Long.class),
        arg("check", Boolean.class),
        arg("listener", com.box.sdk.ProgressListener.class)),

    UPLOAD_NEW_FILE_VERSION(
        com.box.sdk.BoxFile.class,
        "uploadNewFileVersion",
        arg("fileId", String.class),
        arg("fileContent", java.io.InputStream.class),
        arg("modified", java.util.Date.class),
        arg("fileSize", Long.class),
        arg("listener", com.box.sdk.ProgressListener.class));

    private final ApiMethod apiMethod;

    BoxFilesManagerApiMethod(Class<?> resultType, String name, ApiMethodArg... args) {
        this.apiMethod = new ApiMethodImpl(BoxFilesManager.class, resultType, name, args);
    }

    @Override
    public String getName() { return apiMethod.getName(); }

    @Override
    public Class<?> getResultType() { return apiMethod.getResultType(); }

    @Override
    public List<String> getArgNames() { return apiMethod.getArgNames(); }

    @Override
    public List<String> getSetterArgNames() { return apiMethod.getSetterArgNames(); }

    @Override
    public List<Class<?>> getArgTypes() { return apiMethod.getArgTypes(); }

    @Override
    public Method getMethod() { return apiMethod.getMethod(); }
}
