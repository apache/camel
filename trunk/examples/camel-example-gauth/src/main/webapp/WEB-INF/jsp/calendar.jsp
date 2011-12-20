<%@ include file="top.jsp" %>
Here's the list of names of your public and private Google calendars:
<p>
<% for (String calendarName : (java.util.List<String>)request.getAttribute("calendarNames")) { %>
<%= calendarName %><br>
<% } %>
<p>
<a href="https://www.google.com/accounts/IssuedAuthSubTokens">Click here</a> if 
you want to revoke access to your Google Calendar.
<%@ include file="bottom.jsp" %>
