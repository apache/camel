package org.apache.camel.impl;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;

/**
 *
 * JSON API test object
 *
 */
@Type("book")
public class MyBook {
    @Id
    private String isbn;
    private String title;

    @Relationship("author")
    private MyAuthor author;

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public MyAuthor getAuthor() {
        return author;
    }

    public void setAuthor(MyAuthor author) {
        this.author = author;
    }
}
