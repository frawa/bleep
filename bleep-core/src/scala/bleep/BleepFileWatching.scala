package bleep

import bleep.logging.Logger
import bleep.model.assertUsed
import bloop.config.Config

import java.nio.file.Path
import scala.collection.compat._

object BleepFileWatching {
  assertUsed(immutable.LazyList) // silence warning

  def projectPathsMapping(started: Started, projects: Array[model.CrossProjectName]): Map[Path, Seq[model.CrossProjectName]] = {
    val withTransitiveDeps: Array[model.CrossProjectName] =
      (projects ++ projects.flatMap(x => started.build.transitiveDependenciesFor(x).keys)).distinct

    val sourceProjectPairs: Array[(Path, model.CrossProjectName)] =
      withTransitiveDeps.flatMap { name =>
        val bloopProject = started.bloopProjects(name)

        val fromSourcegen = bloopProject.sourceGenerators.toArray.flatten.flatMap(_.sourcesGlobs).flatMap {
          case Config.SourcesGlobs(directory, None, List("glob:**"), Nil) => Some(directory)
          case Config.SourcesGlobs(_, None, List("glob:bleep.yaml"), Nil) => None
          case illegal =>
            sys.error(
              s"implementation restriction: bleep has apparently started to use more of the `Config.SourcesGlobs` structure. This codepath also needs to support $illegal "
            )
        }
        (bloopProject.sources ++ bloopProject.resources.getOrElse(Nil) ++ fromSourcegen).map(path => (path, name))
      }

    sourceProjectPairs.toSeq.groupMap { case (p, _) => p } { case (_, name) => name }
  }

  def projects(started: Started, projects: Array[model.CrossProjectName])(
      onChange: Set[model.CrossProjectName] => Unit
  ): FileWatching.TypedWatcher[model.CrossProjectName] =
    FileWatching(started.logger, projectPathsMapping(started, projects))(onChange)

  def build(logger: Logger, existingBuild: BuildLoader.Existing)(onChange: () => Unit): FileWatching.Watcher =
    FileWatching(logger, Map(existingBuild.bleepYaml -> List(())))(_ => onChange())
}