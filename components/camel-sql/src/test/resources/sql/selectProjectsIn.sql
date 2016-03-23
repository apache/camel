-- this is a comment
select *
from projects
where project in (:#in:names)
order by id