package org.apache.camel;

/**
 * This enum set various categories options into the UriEndpoint.
 * This can be extended.
 */
public enum Category {
    CLOUD("cloud"),
    CACHE("cache"),
    CONTAINER("container"),
    DATABASE("database"),
    FILE("file"),
    MAIL("mail"),
    MESSAGING("messaging");


    private final String value;

    Category(final String value) {
        this.value = value;
    }

    /**
     * Returns the string representation of this value
     * @return Returns the string representation of this value
     */
    public String getValue() {
        return this.value;
    }
}
