/*
 * Camel ApiMethod Enumeration generated by camel-api-component-maven-plugin
 */
package org.apache.camel.component.google.mail.internal;

import java.lang.reflect.Method;
import java.util.List;

import com.google.api.services.gmail.Gmail.Users.Threads;

import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodArg;
import org.apache.camel.support.component.ApiMethodImpl;

import static org.apache.camel.support.component.ApiMethodArg.arg;
import static org.apache.camel.support.component.ApiMethodArg.setter;

/**
 * Camel {@link ApiMethod} Enumeration for com.google.api.services.gmail.Gmail$Users$Threads
 */
public enum GmailUsersThreadsApiMethod implements ApiMethod {

    DELETE(
        com.google.api.services.gmail.Gmail.Users.Threads.Delete.class,
        "delete",
        arg("userId", String.class),
        arg("id", String.class)),

    GET(
        com.google.api.services.gmail.Gmail.Users.Threads.Get.class,
        "get",
        arg("userId", String.class),
        arg("id", String.class),
        setter("format", String.class),
        setter("metadataHeaders", java.util.List.class)),

    LIST(
        com.google.api.services.gmail.Gmail.Users.Threads.List.class,
        "list",
        arg("userId", String.class),
        setter("includeSpamTrash", Boolean.class),
        setter("labelIds", java.util.List.class),
        setter("maxResults", Long.class),
        setter("pageToken", String.class),
        setter("q", String.class)),

    MODIFY(
        com.google.api.services.gmail.Gmail.Users.Threads.Modify.class,
        "modify",
        arg("userId", String.class),
        arg("id", String.class),
        arg("content", com.google.api.services.gmail.model.ModifyThreadRequest.class)),

    TRASH(
        com.google.api.services.gmail.Gmail.Users.Threads.Trash.class,
        "trash",
        arg("userId", String.class),
        arg("id", String.class)),

    UNTRASH(
        com.google.api.services.gmail.Gmail.Users.Threads.Untrash.class,
        "untrash",
        arg("userId", String.class),
        arg("id", String.class));

    private final ApiMethod apiMethod;

    GmailUsersThreadsApiMethod(Class<?> resultType, String name, ApiMethodArg... args) {
        this.apiMethod = new ApiMethodImpl(Threads.class, resultType, name, args);
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
