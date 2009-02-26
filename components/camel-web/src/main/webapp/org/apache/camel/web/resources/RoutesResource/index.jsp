<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>Routes</title>
</head>
<body>

<h2>Routes</h2>

<table>
  <tr>
    <th>Route</th>
    <th colspan="2">Status</th>
  </tr>
<ul>
  <c:forEach var="i" items="${it.routes}">
  <tr>
    <td>
      <a href='<c:url value="/routes/${i.id}"/>'>${i.shortName}</a> ${i.description.text}
    </td>
    <td class="${i.status}">
      ${i.status}
    </td>
    <td>
      <form action='<c:url value="/routes/${i.id}/status"/>' method="POST" name="setStatus">
      <c:if test="${i.startable}">
        <input type="hidden" name="status" value="start">
        <input type="submit" value="Start">
      </c:if>
      <c:if test="${i.stoppable}">
        <input type="hidden" name="status" value="stop">
        <input type="submit" value="Stop">
      </c:if>
      </form>
    </td>
  </c:forEach>
</ul>
</table>

</body>
</html>
