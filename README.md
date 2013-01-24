# The UC San Diego Library DAMS repository

## Setup

1. Environment

    Create a directory to hold DAMS Repo config and files.  Add DAMS_HOME envronment variable to your shell environment (e.g., ~/.bash_profile):

    ``` sh
    DAMS_HOME=/pub/dams
    ```

    Close any open terminal windows or run ". ~/.bash_profile"


    Clone private_config repo from stash:

    ``` sh
    git clone ssh://git@lib-stash.ucsd.edu:7999/ND/private_config.git
    ```

    Copy private_config/gimili/dams.properties to this directory and edit to match your local settings.

    Setup Ant build.properties

    ``` sh
    catalina.home=/pub/tomcat
    deploy.home=${catalina.home}/webapps
    xsl.home=/pub/dams/xsl
    ```

2. MySQL

    Install MySQL.  On MacOSX, this can be done with [Homebrew](http://mxcl.github.com/homebrew/):

    ``` sh
    brew install mysql
    ```

    Set a root password, and create a new database and user:

    ``` sh
    $ mysqladmin -u root password ABC
    $ mysql -uroot -pABC
    mysql> create database dams;
    mysql> grant all privileges on *.* to 'dams'@'localhost' identified by 'XYZ';
    ```

3. Tomcat

    Download Tomcat 7

    http://tomcat.apache.org/download-70.cgi

    Edit Tomcat conf/server.xml and add to the GlobalNamingResources:

    ``` xml
    <Environment name="dams/home" value="/pub/dams" type="java.lang.String"/>
    <Resource name="jdbc/dams" auth="Container" type="javax.sql.DataSource"
        username="dams" password="XXXX" driverClassName="com.mysql.jdbc.Driver"
        url="jdbc:mysql://localhost:3306/dams" maxActive="10" maxIdle="3"
        maxWait="5000" validationQuery="select 1" logAbandoned="true"
        removeAbandonedTimeout="60" removeAbandoned="true" testOnReturn="true"
        testOnBorrow="true"/>
    ```

    Start Tomcat.

4. Solr

    Create solr home directory with solr.war file, solr.xml and a core with
    Hydra's config.  These can be copied from hydra-jetty:

    ``` sh
    cp -a jetty/solr /pub/solr
    cp jetty/webapps/solr.war /pub/solr/solr.war
    ```

    Deploy solr deployment descriptor to tomcat/conf/Catalina/localhost/solr.xml:

    ```xml
    <Context docBase="/pub/solr/solr.war" debug="0" crossContext="true" >
        <Environment name="solr/home" type="java.lang.String" value="/pub/solr" override="true" />
    </Context>
    ```

5. ARK minter

    In your CGI directory (in MacOSX: /Library/WebServer/CGI-Executables/, in RHEL: /var/www/cgi-bin/), create a Perl script called minter:

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

    Make sure Apache is running by minting an ark:

    ```
    http://localhost/cgi-bin/minter
    ```

6. DAMS Repository

    Clone this repo:

    ``` sh
    git clone git@github.com:ucsdlib/damsprivate.git
    ```

    Copy the MySQL JAR file to the Tomcat lib directory:

    ``` sh
    cp srb/lib2/mysql-connector-java-5.0.4-bin.jar /pub/tomcat/lib/
    ```

    Build dams.war and deploy to tomcat

    ``` sh
    ant clean webapp local-deploy
    ```

    Initialize events and object triplestores.

    ``` sh
    tmp/commands/ts-reload.sh
    ```

7. ActiveMQ (Optional)

    Download ActiveMQ

    ```
    http://activemq.apache.org/download.html
    ```

    Copy the activemq.xml config file from private_repository

    ``` sh
    cp private_config/gimili/activemq.xml activemq/conf/
    ```

    Add ACTIVEMQ_HOME to your environment (.profile, etc.)

    ``` sh
    export ACTIVEMQ_HOME=/pub/activemq
    ```

    Start the ActiveMQ daemon

    ``` sh
    $ activemq/bin/activemq start
    ```

    Uncomment the queue.url and queue.name properties in dams.properties and redeploy DAMS Repo.

    Start the solrizerd daemon

    ```sh
    solrizerd start --hydra_home /pub/damspas --host gimili.ucsd.edu --destination activemq:queue.Consumer.hydra.VirtualTopic.dams
    ```
