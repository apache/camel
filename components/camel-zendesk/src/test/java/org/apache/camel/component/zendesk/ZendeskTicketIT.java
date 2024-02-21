/*
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zendesk.client.v2.model.Comment;
import org.zendesk.client.v2.model.Ticket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The integration tests for ticket related Zendesk API.
 */
@EnabledIf(value = "org.apache.camel.component.zendesk.AbstractZendeskTestSupport#hasCredentials",
           disabledReason = "Zendesk credentials were not provided")
public class ZendeskTicketIT extends AbstractZendeskTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ZendeskTicketIT.class);

    @Test
    public void testGetTickets() {
        final Iterable<?> result = requestBody("direct://GETTICKETS", null);

        assertNotNull(result, "getTickets result");
        int count = 0;
        for (Object ticket : result) {
            LOG.info(ticket.toString());
            count++;
        }
        LOG.info("{} ticket(s) in total.", count);
    }

    @Test
    public void testCreateUpdateCommentDeleteTicket() throws Exception {
        // create new ticket
        String ticketSubject = "Test Ticket";
        String ticketDescription = "This is a test ticket from camel-zendesk.";
        Ticket input = new Ticket();
        input.setSubject(ticketSubject);
        input.setDescription(ticketDescription);
        assertNull(input.getId());
        assertNull(input.getCreatedAt());
        Ticket answer = requestBody("direct://CREATETICKET", input);
        assertNotNull(answer.getId());
        assertNotNull(answer.getCreatedAt());
        assertEquals(answer.getCreatedAt(), answer.getUpdatedAt());
        assertEquals(ticketSubject, answer.getSubject());
        assertEquals(ticketDescription, answer.getDescription());

        // update ticket description
        Thread.sleep(3000);
        String ticketSubjectUpdated = ticketSubject + " And updated.";
        input = new Ticket();
        input.setId(answer.getId());
        input.setSubject(ticketSubjectUpdated);
        answer = requestBody("direct://UPDATETICKET", input);
        assertNotEquals(answer.getCreatedAt(), answer.getUpdatedAt());
        assertEquals(ticketSubjectUpdated, answer.getSubject());
        assertEquals(ticketDescription, answer.getDescription());

        // get ticket and compare
        Ticket answer2 = requestBody("direct://GETTICKET", answer.getId());
        assertEquals(answer.getSubject(), answer2.getSubject());
        assertEquals(answer.getDescription(), answer2.getDescription());
        assertEquals(answer.getId(), answer2.getId());
        assertEquals(answer.getCreatedAt(), answer2.getCreatedAt());
        assertEquals(answer.getUpdatedAt(), answer2.getUpdatedAt());

        // add a comment to the ticket
        String commentBody = "This is a comment from camel-zendesk.";
        final Map<String, Object> headers = new HashMap<>();
        assertEquals("ticketId", ZendeskApiMethod.CREATE_COMMENT.getArgNames().get(0));
        assertEquals(long.class, ZendeskApiMethod.CREATE_COMMENT.getArgTypes().get(0));
        headers.put("CamelZendesk.ticketId", answer.getId());
        Comment comment = new Comment();
        comment.setBody(commentBody);
        assertNull(comment.getId());
        assertNull(comment.getCreatedAt());
        assertEquals("comment", ZendeskApiMethod.CREATE_COMMENT.getArgNames().get(1));
        assertEquals(Comment.class, ZendeskApiMethod.CREATE_COMMENT.getArgTypes().get(1));
        headers.put("CamelZendesk.comment", comment);
        requestBodyAndHeaders("direct://CREATECOMMENT", null, headers);
        Iterable iterable = requestBody("direct://GETTICKETCOMMENTS", answer.getId());
        Iterator iterator = iterable.iterator();
        Comment comment1 = (Comment) iterator.next();
        assertEquals(ticketDescription, comment1.getBody());
        assertNotNull(comment1.getId());
        assertNotNull(comment1.getCreatedAt());
        Comment comment2 = (Comment) iterator.next();
        assertEquals(commentBody, comment2.getBody());
        assertNotNull(comment2.getId());
        assertNotNull(comment2.getCreatedAt());

        // delete ticket
        requestBody("direct://DELETETICKET", answer.getId());
        Ticket mustBeDeleted = requestBody("direct://GETTICKET", answer.getId());
        assertNull(mustBeDeleted);
    }

    @Test
    public void testInBodyParams() {
        assertEquals("ticket", ZendeskApiMethod.CREATE_TICKET.getArgNames().get(0));
        assertEquals(Ticket.class, ZendeskApiMethod.CREATE_TICKET.getArgTypes().get(0));
        assertEquals("ticket", ZendeskApiMethod.UPDATE_TICKET.getArgNames().get(0));
        assertEquals(Ticket.class, ZendeskApiMethod.UPDATE_TICKET.getArgTypes().get(0));
        assertEquals("id", ZendeskApiMethod.GET_TICKET.getArgNames().get(0));
        assertEquals(long.class, ZendeskApiMethod.GET_TICKET.getArgTypes().get(0));
        assertEquals("id", ZendeskApiMethod.GET_TICKET_COMMENTS.getArgNames().get(0));
        assertEquals(long.class, ZendeskApiMethod.GET_TICKET_COMMENTS.getArgTypes().get(0));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct://GETTICKETS")
                        .to("zendesk:default/getTickets");

                from("direct://CREATETICKET")
                        .to("zendesk:default/createTicket?inBody=ticket");

                from("direct://UPDATETICKET")
                        .to("zendesk:default/updateTicket?inBody=ticket");

                from("direct://GETTICKET")
                        .to("zendesk:default/getTicket?inBody=id");

                from("direct://CREATECOMMENT")
                        .to("zendesk:default/createComment");

                from("direct://GETTICKETCOMMENTS")
                        .to("zendesk:default/getTicketComments?inBody=id");

                from("direct://DELETETICKET")
                        .to("zendesk:default/deleteTicket?inBody=id");
            }
        };
    }
}
