name         := "reactive-viz"
version      := "1.0"
scalaVersion := "2.11.5"

resolvers ++= Seq(
  "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
  "spray repo stable" at "http://repo.spray.io/"
)

val sprayVersion = "1.3.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka"   %% "akka-stream-experimental"      % "1.0-M2",
  "com.typesafe.akka"   %% "akka-persistence-experimental" % "2.3.7",
  "com.typesafe.play"   %% "play-json"                     % "2.3.6",
  "io.spray"            %% "spray-client"                  % sprayVersion,
  "io.spray"            %% "spray-routing"                 % sprayVersion,
  "com.wandoulabs.akka" %% "spray-websocket"               % "0.1.3",
  "commons-io"          %  "commons-io"                    % "2.4"
)

Revolver.settings
