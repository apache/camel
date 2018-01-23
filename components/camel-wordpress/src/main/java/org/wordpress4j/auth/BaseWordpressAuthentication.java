package org.wordpress4j.auth;

import com.google.common.base.Strings;

abstract class BaseWordpressAuthentication implements WordpressAuthentication {

    protected String username;
    protected String password;

    public BaseWordpressAuthentication() {

    }

    public BaseWordpressAuthentication(final String username, final String password) {
        this.password = password;
        this.username = username;
    }

    @Override
    public final void setPassword(String pwd) {
        this.password =  pwd;
    }

    @Override
    public final void setUsername(String user) {
        this.username = user;
    }

    public final String getPassword() {
        return password;
    }
    
    @Override
    public final String getUsername() {
        return username;
    }
    
    protected final boolean isCredentialsSet() {
        return !Strings.isNullOrEmpty(username) && !Strings.isNullOrEmpty(password);
    }
}
