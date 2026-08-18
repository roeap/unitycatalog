import sbt.Keys.*
import sbt.*

import java.nio.file.Files
import scala.sys.process.*
import scala.util.{Failure, Success, Try}
object Tarball {
  lazy val createTarball = taskKey[Unit]("Create a tarball for the project")

  def createTarballSettings(): Def.SettingsDefinition = {
    Seq(
      createTarball := {
        val log = streams.value.log

        // Output directory for the tarball
        val outputDir = target.value / "dist"
        val projectJarFiles = Seq(
          (LocalProject("server") / Compile / packageBin).value.getAbsoluteFile,
          (LocalProject("cli") / Compile / packageBin).value.getAbsoluteFile,
          (LocalProject("client") / Compile / packageBin).value.getAbsoluteFile,
          (LocalProject("controlApi") / Compile / packageBin).value.getAbsoluteFile,
          (LocalProject("serverModels") / Compile / packageBin).value.getAbsoluteFile
        )
        val scriptsDir = baseDirectory.value / "bin"
        val etcDir = baseDirectory.value / "etc"

        // All JAR files in the classpath
        val allJars = (LocalProject("server") / Compile / managedClasspath).value.files ++
          (LocalProject("cli") / Compile / managedClasspath).value.files ++
          projectJarFiles

        // Clean and create the output directory
        IO.delete(outputDir)
        IO.createDirectory(outputDir)

        // Copy the JAR file to the output directory
        allJars.foreach { jarFile =>
          IO.copyFile(jarFile, outputDir / "jars" / jarFile.getName)
        }
        // Record the classpath using paths relative to the tarball root
        // (jars/<name>.jar), NOT the absolute build-host cache paths of the
        // source jars. This keeps the tarball portable: the jars live under
        // jars/ once extracted, and bin/start-uc-server resolves these entries
        // against the tarball root. Recording absolute source paths would tie
        // the tarball to the machine that built it.
        val classpathFile = outputDir / "jars" / "classpath"
        val relativeEntries = allJars.map(jarFile => s"jars/${jarFile.getName}")
        Files.write(classpathFile.toPath, relativeEntries.mkString(":").getBytes)
        // Copy the script files to the output directory
        IO.copyDirectory(scriptsDir, outputDir / "bin")
        // Copy the etc files to the output directory
        IO.copyDirectory(etcDir, outputDir / "etc")

        // Create the tarball
        val tarballFile = target.value / s"${name.value}-${version.value}.tar.gz"
        log.info(s"Creating tarball at $tarballFile")

        // Execute the tar command
        val tarCommand = Seq(
          "tar",
          "-czvf",
          tarballFile.getAbsolutePath,
          "-C",
          outputDir.getAbsolutePath,
          "."
        )

        Try(Process(tarCommand).!) match {
          case Success(0) => log.info(s"Tarball created successfully: $tarballFile")
          case Success(exitCode) => sys.error(s"Tarball creation failed with exit code $exitCode")
          case Failure(exception) =>
            sys.error(s"Tarball creation failed with exception: ${exception.getMessage}")
        }

        // Clean up the output directory
        IO.delete(outputDir)
        log.info(s"Deleted temporary directory: $outputDir")
      }
    )
  }
}
