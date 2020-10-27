
CREATE DATABASE if not exists XXXX CHARACTER SET utf8 COLLATE utf8_bin;

#..
CREATE DATABASE if not exists XXXXfile CHARACTER SET utf8 COLLATE utf8_bin;

CREATE DATABASE if not exists XXXXsource CHARACTER SET utf8 COLLATE utf8_bin;

create user 'XXXX'@'%' identified by 'XXXX@7jiaj';

GRANT ALL PRIVILEGES ON XXXX.* TO 'XXXX'@'%';
GRANT ALL PRIVILEGES ON XXXXfile.* TO 'XXXX'@'%';

GRANT ALL PRIVILEGES ON XXXXsource.* TO 'XXXX'@'%';



GRANT FILE  ON *.* TO XXXX;


create user 'XXXXebi'@'%' identified by 'XXXX@2121';

GRANT SELECT ON XXXX.* TO 'XXXXebi'@'%';

GRANT CREATE TEMPORARY TABLES ON XXXX.* TO 'XXXXebi'@'%';