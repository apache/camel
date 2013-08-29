package org.apache.camel.facebook;

/**
 * Common constants.
 */
public interface FacebookConstants {

    // reading options property name and prefix for uri property
    String READING_PPROPERTY = "reading";
    String READING_PREFIX = READING_PPROPERTY + ".";

    // property name prefix for exchange 'in' headers
    String FACEBOOK_PROPERTY_PREFIX = "CamelFacebook.";

    // property name for exchange 'in' body
    String IN_BODY_PROPERTY = "inBody";

    String FACEBOOK_THREAD_PROFILE_NAME = "CamelFacebook";

    // date format used by Facebook Reading since and until fields
    String FACEBOOK_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";

}
