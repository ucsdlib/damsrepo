The UC San Diego Library DAMS repository

Setup

1. Setup MySQL, create a new database, and add a new user.

2. Clone private_config repo from stash for config file:

    ssh://git@lib-stash.ucsd.edu:7999/ND/private_config.git

3. Create a directory to hold DAMS Repo config and files.  Copy private_config/gimili/dams.properties to this directory and edit to match your local settings.

4. Download Tomcat 7:

    http://tomcat.apache.org/download-70.cgi

5. Edit Tomcat conf/server.xml and add to the GlobalNamingResources:

```
    <Environment name="dams/home" value="/pub/data1/dams"
      type="java.lang.String"/>
    <Resource name="jdbc/dams" auth="Container" type="javax.sql.DataSource"
      username="dams" password="XXXX" driverClassName="com.mysql.jdbc.Driver"
      url="jdbc:mysql://localhost:3306/dams" maxActive="10" maxIdle="3"
      maxWait="5000" validationQuery="select 1" logAbandoned="true"
      removeAbandonedTimeout="60" removeAbandoned="true" testOnReturn="true"
      testOnBorrow="true"/>
```
   Start Tomcat.

6. Setup Ant build.properties

```
catalina.home=/usr/local/tomcat
deploy.home=${catalina.home}/webapps
xsl.home=/home/escowles/tmp/dams/xsl
```

7. Clone this repo:

    git@github.com:ucsdlib/damsprivate.git

8. Build dams.war and deploy to tomcat

```
ant clean webapp local-deploy
```
