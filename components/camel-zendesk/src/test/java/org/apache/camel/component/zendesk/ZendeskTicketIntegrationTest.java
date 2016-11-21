/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.zendesk;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.zendesk.internal.ZendeskApiMethod;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zendesk.client.v2.model.Comment;
import org.zendesk.client.v2.model.Ticket;

/**
 * The integration tests for ticket related Zendesk API.
 */
public class ZendeskTicketIntegrationTest extends AbstractZendeskTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ZendeskTicketIntegrationTest.class);

    @Test
    public void testGetTickets() throws Exception {
        final Iterable<?> result = requestBody("direct://GETTICKETS", null);

        assertNotNull("getTickets result", result);
        int count = 0;
        for (Object ticket : result) {
            LOG.info(ticket.toString());
            count++;
        }
        LOG.info(count + " ticket(s) in total.");
    }

    @Test
    public void testCreateUpdateCommentDeleteTicket() throws Exception {
        // create new ticket
        String ticketSubject = "Test Ticket";
        String ticketDescription = "This is a test ticket from camel-zendesk.";
        Ticket input = new Ticket();
        input.setSubject(ticketSubject);
        input.setDescription(ticketDescription);
        Assert.assertNull(input.getId());
        Assert.assertNull(input.getCreatedAt());
        Ticket answer = requestBody("direct://CREATETICKET", input);
        Assert.assertNotNull(answer.getId());
        Assert.assertNotNull(answer.getCreatedAt());
        Assert.assertEquals(answer.getCreatedAt(), answer.getUpdatedAt());
        Assert.assertEquals(ticketSubject, answer.getSubject());
        Assert.assertEquals(ticketDescription, answer.getDescription());

        // update ticket description
        Thread.sleep(3000);
        String ticketSubjectUpdated = ticketSubject + " And updated.";
        input = new Ticket();
        input.setId(answer.getId());
        input.setSubject(ticketSubjectUpdated);
        answer = requestBody("direct://UPDATETICKET", input);
        Assert.assertNotEquals(answer.getCreatedAt(), answer.getUpdatedAt());
        Assert.assertEquals(ticketSubjectUpdated, answer.getSubject());
        Assert.assertEquals(ticketDescription, answer.getDescription());

        // get ticket and compare
        Ticket answer2 = requestBody("direct://GETTICKET", answer.getId());
        Assert.assertEquals(answer.getSubject(), answer2.getSubject());
        Assert.assertEquals(answer.getDescription(), answer2.getDescription());
        Assert.assertEquals(answer.getId(), answer2.getId());
        Assert.assertEquals(answer.getCreatedAt(), answer2.getCreatedAt());
        Assert.assertEquals(answer.getUpdatedAt(), answer2.getUpdatedAt());

        // add a comment to the ticket
        String commentBody = "This is a comment from camel-zendesk.";
        final Map<String, Object> headers = new HashMap<String, Object>();
        Assert.assertEquals("ticketId", ZendeskApiMethod.CREATECOMMENT.getArgNames().get(0));
        Assert.assertEquals(long.class, ZendeskApiMethod.CREATECOMMENT.getArgTypes().get(0));
        headers.put("CamelZendesk.ticketId", answer.getId());
        Comment comment = new Comment();
        comment.setBody(commentBody);
        Assert.assertNull(comment.getId());
        Assert.assertNull(comment.getCreatedAt());
        Assert.assertEquals("comment", ZendeskApiMethod.CREATECOMMENT.getArgNames().get(1));
        Assert.assertEquals(Comment.class, ZendeskApiMethod.CREATECOMMENT.getArgTypes().get(1));
        headers.put("CamelZendesk.comment", comment);
        requestBodyAndHeaders("direct://CREATECOMMENT", null, headers);
        Iterable iterable = requestBody("direct://GETTICKETCOMMENTS", answer.getId());
        Iterator iterator = iterable.iterator();
        Comment comment1 = (Comment)iterator.next();
        Assert.assertEquals(ticketDescription, comment1.getBody());
        Assert.assertNotNull(comment1.getId());
        Assert.assertNotNull(comment1.getCreatedAt());
        Comment comment2 = (Comment)iterator.next();
        Assert.assertEquals(commentBody, comment2.getBody());
        Assert.assertNotNull(comment2.getId());
        Assert.assertNotNull(comment2.getCreatedAt());

        // delete ticket
        requestBody("direct://DELETETICKET", answer.getId());
        Ticket mustBeDeleted = requestBody("direct://GETTICKET", answer.getId());
        Assert.assertNull(mustBeDeleted);
    }

    @Test
    public void testInBodyParams() {
        Assert.assertEquals("ticket", ZendeskApiMethod.CREATETICKET.getArgNames().get(0));
        Assert.assertEquals(Ticket.class, ZendeskApiMethod.CREATETICKET.getArgTypes().get(0));
        Assert.assertEquals("ticket", ZendeskApiMethod.UPDATETICKET.getArgNames().get(0));
        Assert.assertEquals(Ticket.class, ZendeskApiMethod.UPDATETICKET.getArgTypes().get(0));
        Assert.assertEquals("id", ZendeskApiMethod.GETTICKET.getArgNames().get(0));
        Assert.assertEquals(long.class, ZendeskApiMethod.GETTICKET.getArgTypes().get(0));
        Assert.assertEquals("id", ZendeskApiMethod.GETTICKETCOMMENTS.getArgNames().get(0));
        Assert.assertEquals(long.class, ZendeskApiMethod.GETTICKETCOMMENTS.getArgTypes().get(0));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct://GETTICKETS")
                    .to("zendesk://getTickets");

                from("direct://CREATETICKET")
                    .to("zendesk://createTicket?inBody=ticket");

                from("direct://UPDATETICKET")
                    .to("zendesk://updateTicket?inBody=ticket");

                from("direct://GETTICKET")
                    .to("zendesk://getTicket?inBody=id");

                from("direct://CREATECOMMENT")
                    .to("zendesk://createComment");

                from("direct://GETTICKETCOMMENTS")
                    .to("zendesk://getTicketComments?inBody=id");

                from("direct://DELETETICKET")
                    .to("zendesk://deleteTicket?inBody=id");
            }
        };
    }
}
