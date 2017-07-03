package org.plovr.webdriver;

import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.Timeouts;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;

public class TestRunner {

  private final URL url;

  private final List<WebDriverFactory> driverFactories;

  private final int timeout;

  /**
   *
   * @param url
   * @param driverFactories
   * @param timeout in milliseconds
   */
  public TestRunner(URL url, List<WebDriverFactory> driverFactories, int timeout) {
    Preconditions.checkNotNull(url);
    Preconditions.checkNotNull(driverFactories);
    Preconditions.checkArgument(!driverFactories.isEmpty());
    Preconditions.checkArgument(timeout > 0);
    this.url = url;
    this.driverFactories = ImmutableList.copyOf(driverFactories);
    this.timeout = timeout;
  }

  // TODO: This method should be called in a thread.
  public boolean run() {
    boolean isSuccess = true;
    for (WebDriverFactory driverFactory : driverFactories) {
      WebDriver driver = driverFactory.newInstance();
      TestRunnerResult result = run(driver);
      isSuccess &= result.isSuccess;

      String driverName = driver.getClass().getName();
      if (result.isSuccess) {
        System.out.printf("SUCCESS: %s\n%s\n", driverName, result.report);
      } else if (result.isTimeout) {
        System.err.printf("FAILURE: %s timed out\n", driverName);
      } else {
        System.err.printf("FAILURE: %s\n%s\n", driverName, result.report);
      }
    }
    return isSuccess;
  }

  private TestRunnerResult run(WebDriver driver) {
    driver.get(url.toString());
    try {
      return runTest(driver);
    } finally {
      driver.quit();
    }
  }

  private TestRunnerResult runTest(WebDriver driver) {
    String js = String.format(
        "var callback = arguments[arguments.length - 1];" +
        "var maxTime = new Date().getTime() + %d;" +
        "var setIntervalId = setInterval(function() {" +
        "  var testRunner = window.G_testRunner;" +
        "  var result;" +
        "  if (testRunner && testRunner.isFinished()) {" +
        "    result = {" +
        "      isSuccess: testRunner.isSuccess()," +
        "      isTimeout: false," +
        "      report: testRunner.getReport(true /* verbose */)" +
        "    };" +
        "  } else if (new Date().getTime() > maxTime) {" +
        "    result = {" +
        "      isSuccess: false," +
        "      isTimeout: true," +
        "      report: null" +
        "    };" +
        "  } else {" +
        "    return;" +
        "  }" +
        "  clearInterval(setIntervalId);" +
        "  callback(JSON.stringify(result));" +
        "}, 1000);",
        timeout);

    // Add an extra second for padding.
    Timeouts timeouts = driver.manage().timeouts();
    int timeoutWithPadding = timeout + 1000;
    timeouts.setScriptTimeout(timeoutWithPadding, TimeUnit.MILLISECONDS);

    Object result = ((JavascriptExecutor)driver).executeAsyncScript(js);
    if (result == null) {
      throw new RuntimeException("Test did not return a value");
    }
    if (!(result instanceof String)) {
      throw new RuntimeException("Test returned something other than JSON");
    }
    String jsonFromTest = (String)result;
    TestRunnerResult testRunnerResult = new Gson().fromJson(jsonFromTest,
        TestRunnerResult.class);
    if (testRunnerResult == null) {
      throw new RuntimeException("Failed to parse JSON as TestRunnerResult");
    } else {
      return testRunnerResult;
    }
  }

  private static class TestRunnerResult {
    public boolean isSuccess;
    public boolean isTimeout;
    public String report;
  }
}
