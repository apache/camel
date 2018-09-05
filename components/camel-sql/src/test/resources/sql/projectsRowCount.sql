-- this is a comment
select count(*) rowcount, license
from projects
where id=:#lowId or id=2 or id=3
group by license