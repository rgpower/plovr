package org.plovr.cli;

import java.io.IOException;

import org.plovr.util.VersionUtil;


public class InfoCommand extends AbstractCommandRunner<InfoCommandOptions> {

  @Override
  InfoCommandOptions createOptions() {
    return new InfoCommandOptions();
  }

  @Override
  String getUsageIntro() {
    return "Display the versions of the Closure Tools packaged with plovr";
  }

  @Override
  int runCommandWithOptions(InfoCommandOptions options) throws IOException {
    String libraryRevision = VersionUtil.getRevision("closure-library");
    String compilerRevision = VersionUtil.getRevision("closure-compiler");
    String templatesRevision = VersionUtil.getRevision("closure-templates");
    String stylesheetsRevision = VersionUtil.getRevision("closure-stylesheets");
    String plovrRevision = VersionUtil.getRevision("plovr");

    System.out.println(String.format("plovr built from revision %s", plovrRevision));
    System.out.println("Revision numbers for embedded Closure Tools:");
    final String INFO_FORMAT = "%-20s: %10s";
    System.out.println(String.format(INFO_FORMAT, "Closure Library", libraryRevision));
    System.out.println(String.format(INFO_FORMAT, "Closure Compiler", compilerRevision));
    System.out.println(String.format(INFO_FORMAT, "Closure Templates", templatesRevision));
    System.out.println(String.format(INFO_FORMAT, "Closure Stylesheets", stylesheetsRevision));
    return 0;
  }
}
