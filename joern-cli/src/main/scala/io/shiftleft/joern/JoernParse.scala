package io.shiftleft.joern

import io.shiftleft.fuzzyc2cpg.FuzzyC2Cpg
import io.shiftleft.joern.plume.PlumeCpgGenerator

import scala.util.control.NonFatal

object JoernParse extends App {
  val DEFAULT_CPG_OUT_FILE = "cpg.bin"

  parseConfig.foreach { config =>
    try {
      generateCpg(config)
    } catch {
      case NonFatal(ex) =>
        println("Error: Failed to generate/enhance CPG.", ex)
    }
  }

  def generateCpg(config: ParserConfig): Unit = {
    if (!config.enhanceOnly) {
      config.language match {
        case "c" =>
          createCpgFromCSourceCode(config)
        case "java" =>
          PlumeCpgGenerator.createCpgForJava(config)
        case _ =>
          println(s"Error: Language ${config.language} not recognized")
          return
      }
    }

    if (config.enhance && config.language != "java") {
      Cpg2Scpg.run(config.outputCpgFile, config.dataFlow).close()
    }

  }

  private def createCpgFromCSourceCode(config: ParserConfig): Unit = {
    val fuzzyc = new FuzzyC2Cpg()
    if (config.preprocessorConfig.usePreprocessor) {
      fuzzyc.runWithPreprocessorAndOutput(
        config.inputPaths,
        config.cFrontendConfig.sourceFileExtensions,
        config.preprocessorConfig.includeFiles,
        config.preprocessorConfig.includePaths,
        config.preprocessorConfig.defines,
        config.preprocessorConfig.undefines,
        config.preprocessorConfig.preprocessorExecutable
      )
    } else {
      fuzzyc
        .runAndOutput(config.inputPaths, config.cFrontendConfig.sourceFileExtensions, Some(config.outputCpgFile))
        .close()
    }
  }

  case class ParserConfig(inputPaths: Set[String] = Set.empty,
                          outputCpgFile: String = DEFAULT_CPG_OUT_FILE,
                          enhance: Boolean = true,
                          dataFlow: Boolean = true,
                          enhanceOnly: Boolean = false,
                          language: String = "c",
                          cFrontendConfig: CFrontendConfig = CFrontendConfig(),
                          preprocessorConfig: PreprocessorConfig = PreprocessorConfig())

  case class CFrontendConfig(sourceFileExtensions: Set[String] = Set(".c", ".cc", ".cpp", ".h", ".hpp"))

  case class PreprocessorConfig(preprocessorExecutable: String = "./bin/fuzzyppcli",
                                verbose: Boolean = true,
                                includeFiles: Set[String] = Set.empty,
                                includePaths: Set[String] = Set.empty,
                                defines: Set[String] = Set.empty,
                                undefines: Set[String] = Set.empty) {
    val usePreprocessor: Boolean =
      includeFiles.nonEmpty || includePaths.nonEmpty || defines.nonEmpty || undefines.nonEmpty
  }

  def parseConfig: Option[ParserConfig] =
    new scopt.OptionParser[ParserConfig]("joern-parse") {

      arg[String]("input-files")
        .unbounded()
        .text("directories containing: C/C++ source | Java classes | a Java archive (JAR/WAR)")
        .action((x, c) => c.copy(inputPaths = c.inputPaths + x))

      opt[String]("language")
        .text("source language: [c|java]. Default: c")
        .action((x, c) => c.copy(language = x))

      opt[String]("out")
        .text("output filename")
        .action((x, c) => c.copy(outputCpgFile = x))

      note("Enhancement stage")

      opt[Unit]("noenhance")
        .text("do not run enhancement stage")
        .action((x, c) => c.copy(enhance = false))
      opt[Unit]("enhanceonly")
        .text("Only run the enhancement stage")
        .action((x, c) => c.copy(enhanceOnly = true))
      opt[Unit]("nodataflow")
        .text("do not perform data flow analysis in enhancement stage")
        .action((x, c) => c.copy(dataFlow = false))

      note("Options for C/C++")

      opt[String]("source-file-ext")
        .unbounded()
        .text("source file extensions to include when gathering source files. Defaults are .c, .cc, .cpp, .h and .hpp")
        .action((pat, cfg) =>
          cfg.copy(cFrontendConfig =
            cfg.cFrontendConfig.copy(sourceFileExtensions = cfg.cFrontendConfig.sourceFileExtensions + pat)))
      opt[String]("include")
        .unbounded()
        .text("header include files")
        .action((incl, cfg) =>
          cfg.copy(preprocessorConfig =
            cfg.preprocessorConfig.copy(includeFiles = cfg.preprocessorConfig.includeFiles + incl)))
      opt[String]('I', "")
        .unbounded()
        .text("header include paths")
        .action((incl, cfg) =>
          cfg.copy(preprocessorConfig =
            cfg.preprocessorConfig.copy(includePaths = cfg.preprocessorConfig.includePaths + incl)))
      opt[String]('D', "define")
        .unbounded()
        .text("define a name")
        .action((d, cfg) =>
          cfg.copy(preprocessorConfig = cfg.preprocessorConfig.copy(defines = cfg.preprocessorConfig.defines + d)))
      opt[String]('U', "undefine")
        .unbounded()
        .text("undefine a name")
        .action((u, cfg) =>
          cfg.copy(preprocessorConfig = cfg.preprocessorConfig.copy(defines = cfg.preprocessorConfig.undefines + u)))
      opt[String]("preprocessor-executable")
        .text("path to the preprocessor executable")
        .action((s, cfg) => cfg.copy(preprocessorConfig = cfg.preprocessorConfig.copy(preprocessorExecutable = s)))

      note("Misc")
      help("help").text("display this help message")
    }.parse(args, ParserConfig())

}
