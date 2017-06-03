-- this is a comment
select *
from projects
where license = :#${body} and project in (:#in:names)
order by id