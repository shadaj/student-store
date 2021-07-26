enablePlugins(PlayScala)

name := "student-store"
version := "1.0-SNAPSHOT"

scalaVersion := "2.13.6"

libraryDependencies ++= Seq(
  guice,
  jdbc,
  "org.postgresql"          % "postgresql" % "42.2.23",
  "com.h2database"          % "h2"         % "1.4.199",
  evolutions,
  "org.playframework.anorm" %% "anorm"     % "2.6.10",
  ehcache,
  "com.github.tototoshi" %% "scala-csv"       % "1.3.8",
  "com.typesafe.play"    %% "play-joda-forms" % "2.8.8"
)
