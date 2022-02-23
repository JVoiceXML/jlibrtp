# RTP Library for Java

jlibrtp aims to create a library that makes it easy to support RTP (RFC 3550,3551) in Java applications. SRTP (RFC 3771) has been delayed in favor of RFC 4585.

In order to increase compatibility, this library is compiled with JDK1.8.

## Include from from Maven

Configure maven to use Central from your Project Object Model (POM) file.You may do so by
adding the following to your pom.xml:

    <repositories>
      <repository>
        <id>central</id>
        <name>Maven Central</name>
        <layout>default</layout>
        <url>https://repo1.maven.org/maven2</url>
        <snapshots>
            <enabled>false</enabled>
        </snapshots>
      </repository>
    </repositories>

Add jlibrtp as a dependecy to your pom.xml

    <dependency>
      <groupId>org.jvoicexml</groupId>
      <artifactId>org.jlibrtp</artifactId>
      <version>0.2</version>
      <type>module</type>
    </dependency>
    
## Include from Gradle

Add the Maven Central repository to your build.gradle

    repositories {
      mavenCentral()
    }

Add jlibrtp as a an implementation dependency to your build.gradle

    implementation 'org.jvoicexml:org.librtp:0.2'
