package org.apache.camel.impl;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;

/**
*
* JSON API test object
*
*/
@Type("foobar")
public class MyFooBar {
    @Id
    private String foo;

    public MyFooBar(String foo) {
        this.foo = foo;
    }

    public String getFoo() {
        return foo;
    }

    public void setFoo(String foo) {
        this.foo = foo;
    }

}