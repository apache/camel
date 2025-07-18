/*
 * Camel ApiMethod Enumeration generated by camel-api-component-maven-plugin
 */
package org.apache.camel.component.google.mail.internal;

import java.lang.reflect.Method;
import java.util.List;

import com.google.api.services.gmail.Gmail.Users;

import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodArg;
import org.apache.camel.support.component.ApiMethodImpl;

import static org.apache.camel.support.component.ApiMethodArg.arg;
import static org.apache.camel.support.component.ApiMethodArg.setter;

/**
 * Camel {@link ApiMethod} Enumeration for com.google.api.services.gmail.Gmail$Users
 */
public enum GmailUsersApiMethod implements ApiMethod {

    GET_PROFILE(
        com.google.api.services.gmail.Gmail.Users.GetProfile.class,
        "getProfile",
        arg("userId", String.class)),

    STOP(
        com.google.api.services.gmail.Gmail.Users.Stop.class,
        "stop",
        arg("userId", String.class)),

    WATCH(
        com.google.api.services.gmail.Gmail.Users.Watch.class,
        "watch",
        arg("userId", String.class),
        arg("content", com.google.api.services.gmail.model.WatchRequest.class));

    private final ApiMethod apiMethod;

    GmailUsersApiMethod(Class<?> resultType, String name, ApiMethodArg... args) {
        this.apiMethod = new ApiMethodImpl(Users.class, resultType, name, args);
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
