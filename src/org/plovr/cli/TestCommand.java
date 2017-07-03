package org.plovr.cli;

import org.plovr.Config;
import org.plovr.ConfigParser;
import org.plovr.TestHandler;
import org.plovr.webdriver.TestRunner;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TestCommand extends AbstractCommandRunner<TestCommandOptions> {

  final private ExecutorService executorService = Executors.newFixedThreadPool(5);

  @Override
  TestCommandOptions createOptions() {
    return new TestCommandOptions();
  }

  @Override
  @SuppressWarnings("deprecation")
  int runCommandWithOptions(TestCommandOptions options) throws IOException {
    List<String> arguments = options.getArguments();
    if (arguments.size() < 1) {
      printUsage();
      return 1;
    }

    try {
      executorService.submit(new ServeCommandRunner(options)).get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    int exitCode = 0;

    try {
      List<Future<Boolean>> testResults = new ArrayList<>();
      for (String configFile : arguments) {
        Config config = ConfigParser.parseFile(new File(configFile));

        int timeout = options.getTimeout() * 1000;

        Set<String> relativeTestPaths = TestHandler.getRelativeTestFilePaths(config);
        for (final String relativeTestPath : relativeTestPaths) {
          URL url = new URL(String.format("http://localhost:%d/test/%s/%s",
              options.getPort(), config.getId(), relativeTestPath));
          final TestRunner testRunner = new TestRunner(url, config.getWebDriverFactories(), timeout);
          testResults.add(executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
              boolean result = testRunner.run();
              return result;
            }
          }));
        }

        for (Future<Boolean> testResult: testResults) {
          boolean result = false;
          try {
            result = testResult.get();
          } catch (Exception e) {
            e.printStackTrace(System.err);
          }
          if (!result) {
            exitCode = 1;
          }
        }
      }
    } finally {
      // TODO: Create a cleaner API to shut down the server.
      executorService.shutdownNow();
    }

    return exitCode;
  }

  @Override
  String getUsageIntro() {
    return "Specify one or more configs whose tests should be run.";
  }

  private static class ServeCommandRunner implements Runnable {

    private final ServeCommandOptions options;

    private ServeCommandRunner(ServeCommandOptions options) {
      this.options = options;
    }

    public void run() {
      ServeCommand command = new ServeCommand();
      try {
        command.runCommandWithOptions(options);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
