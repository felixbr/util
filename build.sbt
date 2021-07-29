import scoverage.ScoverageKeys

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / excludeLintKeys += scalacOptions // might be actually unused in util-doc module but not sure

// All Twitter library releases are date versioned as YY.MM.patch
val releaseVersion = "21.7.0-SNAPSHOT"

val slf4jVersion = "1.7.30"
val jacksonVersion = "2.11.2"
val mockitoVersion = "3.3.3"
val mockitoScalaVersion = "1.14.8"

val zkVersion = "3.5.6"
val zkClientVersion = "0.0.81"
val zkGroupVersion = "0.0.92"
val zkDependency = "org.apache.zookeeper" % "zookeeper" % zkVersion excludeAll (
  ExclusionRule("com.sun.jdmk", "jmxtools"),
  ExclusionRule("com.sun.jmx", "jmxri"),
  ExclusionRule("javax.jms", "jms")
)

val guavaLib = "com.google.guava" % "guava" % "25.1-jre"
val caffeineLib = "com.github.ben-manes.caffeine" % "caffeine" % "2.9.1"
val jsr305Lib = "com.google.code.findbugs" % "jsr305" % "2.0.1"
val scalacheckLib = "org.scalacheck" %% "scalacheck" % "1.15.4" % "test"
val slf4jApi = "org.slf4j" % "slf4j-api" % slf4jVersion

def scalatestLib(scalaBinaryVersion: String) =
  if (isScala3(scalaBinaryVersion))
    "org.scalatest" %% "scalatest" % "3.2.9" % "test"
  else
    "org.scalatest" %% "scalatest" % "3.1.2" % "test"

def scalatestplusScalacheckLib(scalaBinaryVersion: String) =
  if (isScala3(scalaBinaryVersion))
    "org.scalatestplus" %% "scalacheck-1-15" % "3.2.9.0" % "test"
  else
    "org.scalatestplus" %% "scalacheck-1-14" % "3.1.2.0" % "test"

def scalatestplusJUnitLib(scalaBinaryVersion: String) =
  if (isScala3(scalaBinaryVersion))
    "org.scalatestplus" %% "junit-4-13" % "3.2.9.0" % "test"
  else
    "org.scalatestplus" %% "junit-4-12" % "3.1.2.0" % "test"

def scalatestplusMockitoLib(scalaBinaryVersion: String) =
  if (isScala3(scalaBinaryVersion))
    "org.scalatestplus" %% "mockito-3-4" % "3.2.9.0" % "test"
  else
    "org.scalatestplus" %% "mockito-3-3" % "3.1.2.0" % "test"

def travisTestJavaOptions: Seq[String] = {
  // We have some custom configuration for the Travis environment
  // https://docs.travis-ci.com/user/environment-variables/#default-environment-variables
  val travisBuild = sys.env.getOrElse("TRAVIS", "false").toBoolean
  if (travisBuild) {
    Seq(
      "-DSKIP_FLAKY=true",
      "-DSKIP_FLAKY_TRAVIS=true"
    )
  } else {
    Seq(
      "-DSKIP_FLAKY=true"
    )
  }
}

def gcJavaOptions: Seq[String] = {
  val javaVersion = System.getProperty("java.version")
  if (javaVersion.startsWith("1.8")) {
    jdk8GcJavaOptions
  } else {
    jdk11GcJavaOptions
  }
}

def jdk8GcJavaOptions: Seq[String] = {
  Seq(
    "-XX:+UseParNewGC",
    "-XX:+UseConcMarkSweepGC",
    "-XX:+CMSParallelRemarkEnabled",
    "-XX:+CMSClassUnloadingEnabled",
    "-XX:ReservedCodeCacheSize=128m",
    "-XX:SurvivorRatio=128",
    "-XX:MaxTenuringThreshold=0",
    "-Xss8M",
    "-Xms512M",
    "-Xmx2G"
  )
}

def jdk11GcJavaOptions: Seq[String] = {
  Seq(
    "-XX:+UseConcMarkSweepGC",
    "-XX:+CMSParallelRemarkEnabled",
    "-XX:+CMSClassUnloadingEnabled",
    "-XX:ReservedCodeCacheSize=128m",
    "-XX:SurvivorRatio=128",
    "-XX:MaxTenuringThreshold=0",
    "-Xss8M",
    "-Xms512M",
    "-Xmx2G"
  )
}

val defaultProjectSettings = Seq(
  scalaVersion := "2.13.6",
  crossScalaVersions := Seq("2.12.12", "2.13.6", "3.0.2-RC1") // TODO Scala3 use 3.0.2 once released
)

def isScala3(scalaBinaryVersion: String): Boolean =
  scalaBinaryVersion == "3"

val baseSettings = Seq(
  version := releaseVersion,
  organization := "com.twitter",
  // Workaround for a scaladoc bug which causes it to choke on empty classpaths.
  Compile / unmanagedClasspath += Attributed.blank(new java.io.File("doesnotexist")),
  libraryDependencies ++= Seq(
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.4",
    // See https://www.scala-sbt.org/0.13/docs/Testing.html#JUnit
    "com.novocode" % "junit-interface" % "0.11" % "test",
    scalatestLib(scalaBinaryVersion.value),
    scalatestplusJUnitLib(scalaBinaryVersion.value),
  ),
  Test / fork := true, // We have to fork to get the JavaOptions
  // Workaround for cross building HealthyQueue.scala, which is not compatible between
  // 2.12- with 2.13+.
  Compile / unmanagedSourceDirectories += {
    val sourceDir = (Compile / sourceDirectory).value
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _)) => sourceDir / "scala-2.13+"
      case Some((2, n)) if n >= 13 => sourceDir / "scala-2.13+"
      case _ => sourceDir / "scala-2.12-"
    }
  },
  ScoverageKeys.coverageHighlighting := true,
  resolvers +=
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  scalacOptions := Seq(
    "-target:jvm-1.8",
    "-unchecked",
    "-deprecation",
    "-feature",
    "-encoding",
    "utf8",
    // Needs -missing-interpolator due to https://issues.scala-lang.org/browse/SI-8761
    "-Xlint:-missing-interpolator",
    "-Yrangepos"
  ) ++ Seq(
    "-source:3.0-migration",
    //"-rewrite",
    "-explain",
    "-explain-types",
  ).filter(_ => isScala3(scalaBinaryVersion.value)),
  // Note: Use -Xlint rather than -Xlint:unchecked when TestThriftStructure
  // warnings are resolved
  javacOptions ++= Seq("-Xlint:unchecked", "-source", "1.8", "-target", "1.8"),
  doc / javacOptions := Seq("-source", "1.8"),
  javaOptions ++= Seq(
    "-Djava.net.preferIPv4Stack=true",
    "-XX:+AggressiveOpts",
    "-server"
  ),
  javaOptions ++= gcJavaOptions,
  Test / javaOptions ++= travisTestJavaOptions,
  // -a: print stack traces for failing asserts
  testOptions += Tests.Argument(TestFrameworks.JUnit, "-a"),
  // This is bad news for things like com.twitter.util.Time
  Test / parallelExecution := false,
  // Sonatype publishing
  Test / publishArtifact := false,
  pomIncludeRepository := { _ => false },
  publishMavenStyle := true,
  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true),
  autoAPIMappings := true,
  apiURL := Some(url("https://twitter.github.io/util/docs/")),
  pomExtra :=
    <url>https://github.com/twitter/util</url>
      <licenses>
        <license>
          <name>Apache License, Version 2.0</name>
          <url>https://www.apache.org/licenses/LICENSE-2.0</url>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:twitter/util.git</url>
        <connection>scm:git:git@github.com:twitter/util.git</connection>
      </scm>
      <developers>
        <developer>
          <id>twitter</id>
          <name>Twitter Inc.</name>
          <url>https://www.twitter.com/</url>
        </developer>
      </developers>,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
)

val sharedSettings = defaultProjectSettings ++ baseSettings

lazy val noPublishSettings = Seq(
  publish / skip := true
)

lazy val util = Project(
  id = "util",
  base = file(".")
).enablePlugins(
    ScalaUnidocPlugin
  ).settings(
    sharedSettings ++
      noPublishSettings ++
      Seq(
        ScalaUnidoc / unidoc / unidocProjectFilter := inAnyProject -- inProjects(utilBenchmark)
      )
  ).aggregate(
    utilApp,
    utilAppLifecycle,
    utilBenchmark,
    utilCache,
    utilCacheGuava,
    utilClassPreloader,
    utilCodec,
    utilCore,
    utilDoc,
    utilFunction,
    utilHashing,
    utilJacksonAnnotations,
    utilJackson,
    utilJvm,
    utilLint,
    utilLogging,
    //utilMock,  // TODO Scala3
    utilReflect,
    utilRegistry,
    utilRouting,
    utilSecurity,
    utilSecurityTestCerts,
    utilSlf4jApi,
    utilSlf4jJulBridge,
    utilStats,
    utilTest,
    utilThrift,
    utilTunable,
    utilValidator,
    utilZk,
    utilZkTest
  )

lazy val utilApp = Project(
  id = "util-app",
  base = file("util-app")
).settings(
    sharedSettings
  ).settings(
    name := "util-app",
    libraryDependencies ++= Seq(
      "org.mockito" % "mockito-core" % mockitoVersion % "test",
      ("org.scalatestplus" %% "mockito-3-3" % "3.1.2.0" % "test").cross(CrossVersion.for3Use2_13)
    )
  ).dependsOn(utilAppLifecycle, utilCore, utilRegistry)

lazy val utilAppLifecycle = Project(
  id = "util-app-lifecycle",
  base = file("util-app-lifecycle")
).settings(
    sharedSettings
  ).settings(
    name := "util-app-lifecycle"
  ).dependsOn(utilCore)

lazy val utilBenchmark = Project(
  id = "util-benchmark",
  base = file("util-benchmark")
).settings(
    sharedSettings
  ).enablePlugins(
    JmhPlugin
  ).settings(
    name := "util-benchmark"
  ).dependsOn(
    utilCore,
    utilHashing,
    utilJackson,
    utilJvm,
    utilReflect,
    utilStats,
    utilValidator
  )

lazy val utilCache = Project(
  id = "util-cache",
  base = file("util-cache")
).settings(
    sharedSettings
  ).settings(
    name := "util-cache",
    libraryDependencies ++= Seq(
      caffeineLib,
      jsr305Lib,
      "org.mockito" % "mockito-core" % mockitoVersion % "test",
      ("org.scalatestplus" %% "mockito-3-3" % "3.1.2.0" % "test").cross(CrossVersion.for3Use2_13)
    )
  ).dependsOn(utilCore)

lazy val utilCacheGuava = Project(
  id = "util-cache-guava",
  base = file("util-cache-guava")
).settings(
    sharedSettings
  ).settings(
    name := "util-cache-guava",
    libraryDependencies ++= Seq(guavaLib, jsr305Lib)
  ).dependsOn(utilCache % "compile->compile;test->test", utilCore)

lazy val utilClassPreloader = Project(
  id = "util-class-preloader",
  base = file("util-class-preloader")
).settings(
    sharedSettings
  ).settings(
    name := "util-class-preloader"
  ).dependsOn(utilCore)

lazy val utilCodec = Project(
  id = "util-codec",
  base = file("util-codec")
).settings(
    sharedSettings
  ).settings(
    name := "util-codec"
  ).dependsOn(utilCore)

lazy val utilCore = Project(
  id = "util-core",
  base = file("util-core")
).settings(
    sharedSettings
  ).settings(
    name := "util-core",
    // Moved some code to 'concurrent-extra' to conform to Pants' 1:1:1 principle (https://www.pantsbuild.org/build_files.html#target-granularity)
    // so that util-core would work better for Pants projects in IntelliJ.
    Compile / unmanagedSourceDirectories += baseDirectory.value / "concurrent-extra",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-parser-combinators" % "2.0.0",
      caffeineLib % "test",
      scalacheckLib,
      "org.mockito" % "mockito-core" % mockitoVersion % "test",
      scalatestplusScalacheckLib(scalaBinaryVersion.value),
      scalatestplusMockitoLib(scalaBinaryVersion.value),
    ),
    Compile / resourceGenerators += Def.task {
      val projectName = name.value
      val file = resourceManaged.value / "com" / "twitter" / projectName / "build.properties"
      val buildRev = scala.sys.process.Process("git" :: "rev-parse" :: "HEAD" :: Nil).!!.trim
      val buildName = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date)
      val contents =
        s"name=$projectName\nversion=${version.value}\nbuild_revision=$buildRev\nbuild_name=$buildName"
      IO.write(file, contents)
      Seq(file)
    }.taskValue,
    Compile / doc / sources := {
      // Monitors.java causes "not found: type Monitor$" (CSL-5034)
      // so exclude it from the sources used for scaladoc
      val previous = (Compile / doc / sources).value
      previous.filterNot(file => file.getName() == "Monitors.java")
    }
  ).dependsOn(utilFunction)

lazy val utilDoc = Project(
  id = "util-doc",
  base = file("doc")
).enablePlugins(
    SphinxPlugin
  ).settings(
    sharedSettings ++
      noPublishSettings ++ Seq(
      doc / scalacOptions ++= Seq("-doc-title", "Util", "-doc-version", version.value),
      Sphinx / includeFilter := ("*.html" | "*.png" | "*.svg" | "*.js" | "*.css" | "*.gif" | "*.txt")
    ))

lazy val utilFunction = Project(
  id = "util-function",
  base = file("util-function")
).settings(
    sharedSettings
  ).settings(
    name := "util-function"
  )

lazy val utilReflect = Project(
  id = "util-reflect",
  base = file("util-reflect")
).settings(
    sharedSettings
  ).settings(
    name := "util-reflect",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )
  ).dependsOn(utilCore)

lazy val utilHashing = Project(
  id = "util-hashing",
  base = file("util-hashing")
).settings(
    sharedSettings
  ).settings(
    name := "util-hashing",
    libraryDependencies ++= Seq(
      scalacheckLib,
      "org.scalatestplus" %% "scalacheck-1-14" % "3.1.2.0" % "test"
    ).map(_.cross(CrossVersion.for3Use2_13))
  ).dependsOn(utilCore % "test")

lazy val utilJacksonAnnotations = Project(
  id = "util-jackson-annotations",
  base = file("util-jackson-annotations")
).settings(
    sharedSettings
  ).settings(
    name := "util-jackson-annotations"
  )

lazy val utilJackson = Project(
  id = "util-jackson",
  base = file("util-jackson")
).settings(
    sharedSettings
  ).settings(
    name := "util-jackson",
    libraryDependencies ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
      "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % jacksonVersion,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion,
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion exclude ("com.google.guava", "guava"),
      "jakarta.validation" % "jakarta.validation-api" % "3.0.0",
      "org.json4s" %% "json4s-core" % "3.6.7",
      "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion % "test",
      scalacheckLib,
      "org.scalatestplus" %% "scalacheck-1-14" % "3.1.2.0" % "test",
      "org.slf4j" % "slf4j-simple" % slf4jVersion % "test"
    )
  ).dependsOn(utilCore, utilJacksonAnnotations, utilMock % Test, utilReflect, utilSlf4jApi, utilValidator)

lazy val utilJvm = Project(
  id = "util-jvm",
  base = file("util-jvm")
).settings(
    sharedSettings
  ).settings(
    name := "util-jvm",
    libraryDependencies ++= Seq(
      "org.mockito" % "mockito-core" % mockitoVersion % "test",
      ("org.scalatestplus" %% "mockito-3-3" % "3.1.2.0" % "test").cross(CrossVersion.for3Use2_13)
    )
  ).dependsOn(utilApp, utilCore, utilStats)

lazy val utilLint = Project(
  id = "util-lint",
  base = file("util-lint")
).settings(
    sharedSettings
  ).settings(
    name := "util-lint"
  ).dependsOn(utilCore)

lazy val utilLogging = Project(
  id = "util-logging",
  base = file("util-logging")
).settings(
    sharedSettings
  ).settings(
    name := "util-logging"
  ).dependsOn(utilCore, utilApp, utilStats)

lazy val utilMock = Project(
  id = "util-mock",
  base = file("util-mock")
).settings(
    sharedSettings
  ).settings(
    name := "util-mock",
    libraryDependencies ++= Seq(
      // only depend on mockito-scala; it will pull in the correct corresponding version of mockito.
      "org.mockito" %% "mockito-scala" % mockitoScalaVersion,
      "org.mockito" %% "mockito-scala-scalatest" % mockitoScalaVersion % "test"
    ).map(_.cross(CrossVersion.for3Use2_13))
  ).dependsOn(utilCore % "test")

lazy val utilRegistry = Project(
  id = "util-registry",
  base = file("util-registry")
).settings(
    sharedSettings
  ).settings(
    name := "util-registry",
    libraryDependencies ++= Seq(
      "org.mockito" % "mockito-core" % mockitoVersion % "test",
      ("org.scalatestplus" %% "mockito-3-3" % "3.1.2.0" % "test").cross(CrossVersion.for3Use2_13)
    )
  ).dependsOn(utilCore)

lazy val utilRouting = Project(
  id = "util-routing",
  base = file("util-routing")
).settings(
    sharedSettings
  ).settings(
    name := "util-routing"
  ).dependsOn(utilCore, utilSlf4jApi)

lazy val utilSlf4jApi = Project(
  id = "util-slf4j-api",
  base = file("util-slf4j-api")
).settings(
    sharedSettings
  ).settings(
    name := "util-slf4j-api",
    libraryDependencies ++= Seq(
      slf4jApi,
      "org.mockito" % "mockito-core" % mockitoVersion % "test",
      "org.slf4j" % "slf4j-simple" % slf4jVersion % "test"
    )
  ).dependsOn(utilCore % "test")

lazy val utilSlf4jJulBridge = Project(
  id = "util-slf4j-jul-bridge",
  base = file("util-slf4j-jul-bridge")
).settings(
    sharedSettings
  ).settings(
    name := "util-slf4j-jul-bridge",
    libraryDependencies ++= Seq(slf4jApi, "org.slf4j" % "jul-to-slf4j" % slf4jVersion)
  ).dependsOn(utilCore, utilSlf4jApi)

lazy val utilSecurity = Project(
  id = "util-security",
  base = file("util-security")
).settings(
    sharedSettings
  ).settings(
    name := "util-security",
    libraryDependencies ++= Seq(
      scalacheckLib,
      "org.scalatestplus" %% "scalacheck-1-14" % "3.1.2.0" % "test"
    ).map(_.cross(CrossVersion.for3Use2_13))
  ).dependsOn(utilCore, utilLogging, utilSecurityTestCerts % "test")

lazy val utilSecurityTestCerts = Project(
  id = "util-security-test-certs",
  base = file("util-security-test-certs")
).settings(
    sharedSettings
  ).settings(
    name := "util-security-test-certs"
  )

lazy val utilStats = Project(
  id = "util-stats",
  base = file("util-stats")
).settings(
    sharedSettings
  ).settings(
    name := "util-stats",
    libraryDependencies ++= Seq(
      caffeineLib,
      jsr305Lib,
      "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
      "org.mockito" % "mockito-core" % mockitoVersion % "test",
    ) ++ Seq(
      scalacheckLib,
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion exclude ("com.google.guava", "guava"),
      "org.scalatestplus" %% "mockito-3-3" % "3.1.2.0" % "test",
      "org.scalatestplus" %% "scalacheck-1-14" % "3.1.2.0" % "test",
    ).map(_.cross(CrossVersion.for3Use2_13)) ++ {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, major)) =>
          Seq("org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.3" % "test")
        case Some((2, major)) if major >= 13 =>
          Seq("org.scala-lang.modules" %% "scala-parallel-collections" % "0.2.0" % "test")
        case _ =>
          Seq()
      }
    }
  ).dependsOn(utilCore, utilLint)

lazy val utilTest = Project(
  id = "util-test",
  base = file("util-test")
).settings(
    sharedSettings
  ).settings(
    name := "util-test",
    libraryDependencies ++= Seq(
      "org.mockito" % "mockito-all" % "1.10.19",
      (if (isScala3(scalaBinaryVersion.value))
        "org.scalatest" %% "scalatest" % "3.2.9"
      else
        "org.scalatest" %% "scalatest" % "3.1.2"),
      ("org.scalatestplus" %% "junit-4-12" % "3.1.2.0").cross(CrossVersion.for3Use2_13),
      ("org.scalatestplus" %% "mockito-1-10" % "3.1.0.0").cross(CrossVersion.for3Use2_13)
    )
  ).dependsOn(utilCore, utilLogging)

lazy val utilThrift = Project(
  id = "util-thrift",
  base = file("util-thrift")
).settings(
    sharedSettings
  ).settings(
    name := "util-thrift",
    libraryDependencies ++= Seq(
      "org.apache.thrift" % "libthrift" % "0.10.0",
      slf4jApi % "provided",
      "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion
    )
  ).dependsOn(utilCodec)

lazy val utilTunable = Project(
  id = "util-tunable",
  base = file("util-tunable")
).settings(
    sharedSettings
  ).settings(
    name := "util-tunable",
    resolvers += // TODO Scala3 remove this once the library is published properly
      "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    libraryDependencies ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
      //("com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion exclude ("com.google.guava", "guava")).cross(CrossVersion.for3Use2_13)
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.13.0-SNAPSHOT", // TODO Scala3 global jackson upgrade first?
    )
  ).dependsOn(utilApp, utilCore)

lazy val utilValidator = Project(
  id = "util-validator",
  base = file("util-validator")
).settings(
    sharedSettings
  ).settings(
    name := "util-validator",
    libraryDependencies ++= Seq(
      caffeineLib,
      "jakarta.validation" % "jakarta.validation-api" % "3.0.0",
      "org.hibernate.validator" % "hibernate-validator" % "7.0.1.Final",
      "org.glassfish" % "jakarta.el" % "4.0.0",
      "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion % "test",
      "org.slf4j" % "slf4j-simple" % slf4jVersion % "test"
    ) ++ Seq(
      "org.json4s" %% "json4s-core" % "3.6.7",
      scalacheckLib,
      "org.scalatestplus" %% "scalacheck-1-14" % "3.1.2.0" % "test",
    ).map(_.cross(CrossVersion.for3Use2_13))
  ).dependsOn(utilCore, utilReflect, utilSlf4jApi)

lazy val utilZk = Project(
  id = "util-zk",
  base = file("util-zk")
).settings(
    sharedSettings
  ).settings(
    name := "util-zk",
    libraryDependencies ++= Seq(
      zkDependency,
      "org.mockito" % "mockito-core" % mockitoVersion % "test",
      ("org.scalatestplus" %% "mockito-3-3" % "3.1.2.0" % "test").cross(CrossVersion.for3Use2_13)
    )
  ).dependsOn(utilCore, utilLogging)

lazy val utilZkTest = Project(
  id = "util-zk-test",
  base = file("util-zk-test")
).settings(
    sharedSettings
  ).settings(
    name := "util-zk-test",
    libraryDependencies += zkDependency
  ).dependsOn(utilCore % "test")
