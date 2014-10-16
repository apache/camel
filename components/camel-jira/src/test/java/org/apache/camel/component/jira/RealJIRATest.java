package org.apache.camel.component.jira;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Created by kearls on 13/10/14.
 */
public class RealJIRATest extends CamelTestSupport {
    private static final String URL = "https://cameljiracomponent.atlassian.net";
    private static final String USERNAME = "kapitano";
    private static final String PASSWORD = "LYQhran9JoEos2t";
    private static final String PROJECT = "camel-jira-component";  // OR key   CAMELJIRA
    private String JIRA_CREDENTIALS = URL + "&username=" + USERNAME + "&password=" + PASSWORD;

    /**
    private String RH_URL="https://issues.jboss.org/";
    private String RH_USERNAME="kearls";
    private String RH_PASSWORD="HWCnfjg63e";
    private String PROJECT="ENTESB";
    private String JIRA_CREDENTIALS = RH_URL + "&username=" + RH_USERNAME + "&password=" + RH_PASSWORD;
     */

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                context.addComponent("jira", new JIRAComponent());
                from("jira://newIssue?serverUrl=" + JIRA_CREDENTIALS + "&jql=project=" + PROJECT)
                        .process(new NewIssueProcessor())
                        .to("mock:result");

                /*from("jira://newComment?serverUrl=" + JIRA_CREDENTIALS
                    + "&jql=RAW(project=CAMELJIRA AND status in (Open, \"Coding In Progress\") AND \"Number of comments\">0)")
                        .to("mock:result");    */
            }
        };
    }


    @Ignore
    @Test
    public void justSitHereForAWhile()  throws Exception {
        System.out.println(">>>> Listening....");
        Thread.sleep(30 * 60 * 1000);
    }


    public class NewIssueProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Message in = exchange.getIn();
            Object issue = in.getBody();
            System.out.println("Issue is a " + issue.getClass().getCanonicalName());
            System.out.println("Got an issue:" + in.getBody().toString());
        }
    }


}
