name := "homework"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache
)

libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.0.0"

play.Project.playScalaSettings
