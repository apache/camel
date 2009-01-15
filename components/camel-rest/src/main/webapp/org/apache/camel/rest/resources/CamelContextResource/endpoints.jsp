<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <title>Endpoints</title>
</head>
<body>

<h2>Endpoints</h2>

<ul>
  <c:forEach var="i" items="${it.endpoints}">
    <li><a href="${i.href}">${i.uri}</a>
  </c:forEach>
</ul>


</body>
</html>
