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
    <th>Status</th>
  </tr>
<ul>
  <c:forEach var="i" items="${it.routes}">
  <tr>
    <td>
      <a href='<c:url value="/routes/${i.id}"/>'>${i.shortName}</a> ${i.description}
    </td>
    <td class="${i.status}">
      ${i.status}
    </td>
  </c:forEach>
</ul>
</table>

</body>
</html>
