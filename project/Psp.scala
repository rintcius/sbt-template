package psp

import sbt._, Keys._

object Sbtx {
  type SettingOf[A]   = Def.Initialize[A]
  type TaskOf[A]      = Def.Initialize[Task[A]]
  type InputTaskOf[A] = Def.Initialize[InputTask[A]]
  type Sets           = Seq[Setting[_]]
  type Strs           = Seq[String]

  def wordSeq(s: String): Strs                = s split "\\s+" filterNot (_ == "") toVector
  def envOr(key: String, value: String): Strs = wordSeq(sys.env.getOrElse(key, value))

  def inCompile(ss: Setting[_]*)             = inConfig(Compile)(ss.toSeq)
  def inTest(ss: Setting[_]*)                = inConfig(Test)(ss.toSeq)
  def inBoth(f: Configuration => Sets): Sets = List(Test, Compile) flatMap f
  def buildBase                              = baseDirectory in ThisBuild
  def javaSpecVersion: String                = sys.props("java.specification.version")

  /** Watch out Jonesy! It's the ol' double-cross!
   *  Why, you...
   *
   *  Given a path like src/main/scala we want that to explode into something like the
   *  following, assuming we're currently building with java 1.7 and scala 2.10.
   *
   *    src/main/scala
   *    src/main/scala_2.10
   *    src/main_1.7/scala
   *    src/main_1.7/scala_2.10
   *
   *  Similarly for main/test, 2.10/2.11, 1.7/1.8.
   */
  def crossScalaSource(config: Configuration) = Def setting {
    val jappend = Seq("", "_" + javaSpecVersion)
    val sappend = Seq("", "_" + scalaBinaryVersion.value)
    val basis   = (sourceDirectory in config).value
    val parent  = basis.getParentFile
    val name    = basis.getName
    for (j <- jappend ; s <- sappend) yield parent / s"$name$j" / s"scala$s"
  }

  /** This separates bytecode by jvm version, so you can switch
   *  between java versions without it always doing a full recompile.
   */
  def crossJvmTarget(c: Configuration) = Def setting {
    buildBase.value / "target" / thisProject.value.id / s"java_$javaSpecVersion"
  }

  implicit class SettingKeyOps[A](val key: SettingKey[A]) {
    // For a project as obsessed with "map" as is sbt, how did they manage to
    // fuck up calling "map" on a setting? It is implicitly converted to a Task
    // or something, and you don't get a setting back.
    def mapValue[B](f: A => B): SettingOf[B] = Def setting f(key.value)
  }
  implicit class KeyPairOps[A, B](val pair: (SettingKey[A], SettingKey[B])) {
    def mapValue[C](f: (A, B) => C): SettingOf[C] = Def setting f(pair._1.value, pair._2.value)
  }

  implicit class ProjectOps(val p: Project) {
    def root: Project = noArtifacts in file(".")
    def noArtifacts: Project = also(
                publish := (()),
           publishLocal := (()),
         Keys.`package` := file(""),
             packageBin := file(""),
      packagedArtifacts := Map()
    )
    def also(m: ModuleID, ms: ModuleID*): Project     = also(libraryDependencies ++= m +: ms)
    def also(s: Setting[_], ss: Setting[_]*): Project = also(s +: ss.toSeq)
    def also(ss: Sets): Project            = p settings (ss: _*)
  }
}