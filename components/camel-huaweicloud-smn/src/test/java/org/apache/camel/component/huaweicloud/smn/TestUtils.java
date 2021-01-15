package org.apache.camel.component.huaweicloud.smn;

import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.getAllServeEvents;

public class TestUtils {
    /**
     * get the request from wiremock serve events which was sent as part of templated publish notification
     * @param serveEvents
     * @return
     */
    public static LoggedRequest retrieveTemplatedNotificationRequest(List<ServeEvent> serveEvents) {
        LoggedRequest loggedRequest = null;
        for(ServeEvent event : getAllServeEvents()) {
            if(event.getRequest().getBodyAsString().contains("\"tags\"")) {
                loggedRequest = event.getRequest();
                break;
            }
        }
        return loggedRequest;
    }

    /**
     * get the request from wiremock serve events which was sent as part of text notification
     * @param serveEvents
     * @return
     */
    public static LoggedRequest retrieveTextNotificationRequest(List<ServeEvent> serveEvents) {
        LoggedRequest loggedRequest = null;
        for(ServeEvent event : getAllServeEvents()) {
            if(!event.getRequest().getBodyAsString().contains("\"tags\"")) {
                loggedRequest = event.getRequest();
                break;
            }
        }
        return loggedRequest;
    }
}
