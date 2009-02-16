<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>Routes</title>
</head>
<body>

<h2>Routes</h2>


<ul>
  <c:forEach var="i" items="${it.routes}">
    <li><a href="${i.id}">${i.shortName}</a> ${i.description}
  </c:forEach>
</ul>

</body>
</html>
