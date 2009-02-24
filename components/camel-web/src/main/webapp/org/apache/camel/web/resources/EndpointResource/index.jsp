<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Endpoint ${it.uri}</title>
    </head>
    <body>

        <h1>Endpoint: ${it.uri}</h1>

        <ul>
          <li><a class='send' href='<c:url value="${it.href}/send"/>'>Send to this endpoint</a></li>
        </ul>

        <c:if test="${it.browsableEndpoint != null}">
          <table>
            <tr>
              <th>Message ID (${fn:length(it.browsableEndpoint.exchanges)} in total)</th>
            </tr>
          <c:forEach items="${it.browsableEndpoint.exchanges}" var="exchange">
            <tr>
              <td><a class='message' href='<c:url value="${it.href}/messages/${exchange.exchangeId}"/>' title="View this message">${exchange.exchangeId}</a></td>
            </tr>
          </c:forEach>
          </table>
        </c:if>
    </body>
</html>
