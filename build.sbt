ThisBuild / scalaVersion     := "2.13.7"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

val zioVersion = "1.0.12"

val circeVersion = "0.14.1"

val zioHttpVersion = "1.0.0.0-RC17"

val jwtCirceVersion = "9.0.2"

val log4jVersion = "2.14.0"

val slickVersion = "3.3.3"

val slickInteropVersion = "0.4"

val bcryptVersion = "0.4.1"

lazy val root = (project in file("."))
  .settings(
    name := "TwoFactorAuthentication",
    libraryDependencies ++= Seq(
      "dev.zio"                     %% "zio"                    % zioVersion,
      "dev.zio"                     %% "zio-streams"            % zioVersion,
      "dev.zio"                     %% "zio-test"               % zioVersion                      % Test,
      "io.d11"                      %% "zhttp"                  % zioHttpVersion,
      "io.circe"                    %% "circe-core"             % circeVersion,
      "io.circe"                    %% "circe-generic"          % circeVersion,
      "io.circe"                    %% "circe-parser"           % circeVersion,
      "com.github.jwt-scala"        %% "jwt-circe"              % jwtCirceVersion,
      "com.typesafe.slick"          %% "slick"                  % slickVersion,
      "com.typesafe.slick"          %% "slick-hikaricp"         % slickVersion,
      "io.scalac"                   %% "zio-slick-interop"      % slickInteropVersion,
    ),

    libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % log4jVersion,
    libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % log4jVersion,
    libraryDependencies += "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4jVersion,
    libraryDependencies += "org.apache.logging.log4j" % "log4j-1.2-api" % log4jVersion,
    libraryDependencies += "mysql" % "mysql-connector-java" % "8.0.23",
    libraryDependencies += "com.h2database" % "h2" % "1.4.200",
    libraryDependencies +=   "de.svenkubiak" % "jBCrypt" % bcryptVersion,

    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )
