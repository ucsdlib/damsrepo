The UC San Diego Library DAMS repository

Setup

1. Add DAMS_HOME envronment variable to your shell environment (e.g., ~/.bash_profile):

   ``` sh
   DAMS_HOME=/pub/dams
   ```

   Close any open terminal windows or run ". ~/.bash_profile"

1. Setup MySQL, create a new database, and add a new user.

   ``` sh
   $ mysqladmin -u root password ABC
   $ mysql -uroot -pABC
   mysql> create database dams;
   mysql> grant all privileges on *.* to 'dams'@'localhost' identified by 'XYZ';
   ```

2. Clone private_config repo from stash for config file:

    ssh://git@lib-stash.ucsd.edu:7999/ND/private_config.git

3. Create a directory to hold DAMS Repo config and files.  Copy private_config/gimili/dams.properties to this directory and edit to match your local settings.

4. Download Tomcat 7:

    http://tomcat.apache.org/download-70.cgi

5. Edit Tomcat conf/server.xml and add to the GlobalNamingResources:

   ``` xml
   <Environment name="dams/home" value="/pub/dams" type="java.lang.String"/>
   <Resource name="jdbc/dams" auth="Container" type="javax.sql.DataSource"
      username="dams" password="XXXX" driverClassName="com.mysql.jdbc.Driver"
      url="jdbc:mysql://localhost:3306/dams" maxActive="10" maxIdle="3"
      maxWait="5000" validationQuery="select 1" logAbandoned="true"
      removeAbandonedTimeout="60" removeAbandoned="true" testOnReturn="true"
      testOnBorrow="true"/>
   ```

6. Start Tomcat.

7. Create solr home directory with solr.war file, solr.xml and a core with
   Hydra's config.  These can be copied from hydra-jetty:

   ``` sh
   cp -a jetty/solr /pub/solr
   cp jetty/webapps/solr.war /pub/solr/solr.war
   ```

8. Deploy solr deployment descriptor to tomcat/conf/Catalina/localhost/solr.xml:

   ```xml
   <Context docBase="/pub/solr/solr.war" debug="0" crossContext="true" >
      <Environment name="solr/home" type="java.lang.String" value="/pub/solr" override="true" />
   </Context>
   ```

9. Setup an ARK minter.  In your CGI directory (in MacOSX: /Library/WebServer/CGI-Executables/, in RHEL: /var/www/cgi-bin/), create a Perl script:

   ```perl
   #!/usr/bin/perl
   open( FILE, "<", "minter.dat" );
   $num = <FILE>;
   close FILE;
   print "Content-Type: text/plain\n\n";
   $n = $ENV{'QUERY_STRING'};
   unless ( $n ) { $n = 1; }
   for ( $i = 0; $i < $n; $i++ )
   {
      $num++;
      print "id: 20775/zz" . sprintf("%08d",$num) . "\n";
   }
   open( FILE, ">", "minter.dat" );
   print FILE $num;
   close FILE;
   ```

   Create the minter data file:

   ``` sh
   touch minter.dat
   chmod a+w minter.dat
   ```

7. Setup Ant build.properties

   ``` sh
   catalina.home=/pub/tomcat
   deploy.home=${catalina.home}/webapps
   xsl.home=/pub/dams/xsl
   ```

8. Clone this repo:

    git@github.com:ucsdlib/damsprivate.git

9. Copy the MySQL JAR file to the Tomcat lib directory:

   ``` sh
   cp srb/lib2/mysql-connector-java-5.0.4-bin.jar /pub/tomcat/lib/
   ```

10. Build dams.war and deploy to tomcat

   ``` sh
   ant clean webapp local-deploy
   ```

11. Initialize events and object triplestores.

   ``` sh
   tmp/commands/ts-reload.sh
   ```
