package org.apache.camel.component.pulsar;

import org.apache.camel.component.pulsar.utils.TopicTypeUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PulsarUri {

    private static final String REGEX = "^pulsar://(?<type>.+)/(?<tenant>.+)/(?<namespace>.+)/(?<topic>.+)$";
    private static final Pattern pattern = Pattern.compile(REGEX);
    private static final String TYPE = "type";
    private static final String TENANT = "tenant";
    private static final String NAMESPACE = "namespace";
    private static final String TOPIC = "topic";

    private final String type;
    private final String tenant;
    private final String namespace;
    private final String topic;

    PulsarUri(String uri) {
        Matcher matcher = pattern.matcher(uri);
        if(matcher.matches()) {
            type = TopicTypeUtils.parse(matcher.group(TYPE));
            tenant = matcher.group(TENANT);
            namespace = matcher.group(NAMESPACE);
            topic = matcher.group(TOPIC);
        } else {
            throw new IllegalArgumentException("Invalid pulsar uri - " + uri);
        }
    }

    public String getType() {
        return type;
    }

    public String getTenant() {
        return tenant;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getTopic() {
        return topic;
    }
}
