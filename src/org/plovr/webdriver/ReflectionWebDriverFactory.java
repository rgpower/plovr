package org.plovr.webdriver;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;

import com.google.common.base.Preconditions;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.util.HashMap;
import java.util.Map;

public final class ReflectionWebDriverFactory implements WebDriverFactory {

  private final String webDriverClassName;
  private final Map<String,Object> capabilityMap;

  /**
   * @param webDriverClassName a fully-qualified class name of a class that
   *     implements {@link WebDriver}, such as
   *     "org.openqa.selenium.firefox.FirefoxDriver" or
   *     "org.openqa.selenium.htmlunit.HtmlUnitDriver"
   */
  public ReflectionWebDriverFactory(String webDriverClassName, Map<String,Object> capabilityMap) {
    Preconditions.checkNotNull(webDriverClassName);
    Preconditions.checkNotNull(capabilityMap);
    this.webDriverClassName = webDriverClassName;
    this.capabilityMap = capabilityMap instanceof  Map ? capabilityMap : new HashMap<String,Object>();
  }

  @Override
  public WebDriver newInstance() {
    try {
      @SuppressWarnings("unchecked")
      Class<? extends WebDriver> clazz = (Class<? extends WebDriver>)Class.
          forName(webDriverClassName);

      DesiredCapabilities capabilities = new DesiredCapabilities(this.capabilityMap);
      WebDriver webDriver =
          clazz.getConstructor(Capabilities.class).newInstance(capabilities);

      return webDriver;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
