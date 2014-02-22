import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "student-store"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    // Add your project dependencies here,
    jdbc,
    anorm,
    cache,
    "com.github.tototoshi" %% "scala-csv" % "1.0.0",
    "postgresql" % "postgresql" % "9.1-901-1.jdbc4"
  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
          
  )

}