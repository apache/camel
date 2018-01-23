package org.wordpress4j.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * A named status for the object.
 */
@JacksonXmlRootElement(localName = "publishableStatus")
public enum PublishableStatus {
    //@formatter:off
    publish, 
    future, 
    draft, 
    pending, 
    @JsonProperty("private")
    private_,
    trash,
    @JsonProperty("auto-draft")
    auto_draft,
    inherit,
    any;
    //@formatter:on

    /***
     * @param arg
     * @return
     * @see <a href=
     *      "https://stackoverflow.com/questions/33357594/java-enum-case-insensitive-jersey-query-param-binding">Java:
     *      Enum case insensitive Jersey Query Param Binding</a>
     */
    public static final PublishableStatus fromString(String arg) {
        arg = "".concat(arg).toLowerCase();
        if (!arg.isEmpty() && arg.startsWith("private")) {
            return private_;
        }
        if (!arg.isEmpty() && arg.startsWith("auto")) {
            return auto_draft;
        }

        return valueOf(arg);
    }
}
