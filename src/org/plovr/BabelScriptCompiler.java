package org.plovr;

import javax.script.Bindings;

import com.google.gson.JsonObject;

/**
 * Java wrapper around the JavaScript BabelScript compiler.
 *
 * @author rickp@celtx.com (Rick Power)
 *
 * If you get 'Method too large' when instantiating this compiler,
 * please see the following:
 *
 * @see <a href="https://bugs.openjdk.java.net/browse/JDK-8136834">JDK 8</a>
 * @see <a href="https://bugs.openjdk.java.net/browse/JDK-8135190">JDK 9</a>
 *
 */
public class BabelScriptCompiler
    extends AbstractJavaScriptBasedCompiler<BabelScriptCompilerException> {

  /**
   * @return the singleton instance of the {@link BabelScriptCompiler}
   */
  public static BabelScriptCompiler getInstance() {
    return BabelScriptCompilerHolder.instance;
  }

  /**
   * Creates a new instance of the BabelScriptCompiler by reading and
   * interpreting the BabelScript compiler's JavaScript source code. This is
   * done once to create a scope that can be reused by
   * {@link #compile(String, String)}.
   */
  private BabelScriptCompiler() {
    super("org/plovr/babel-core-5.8.25-browser.js");
  }

  @Override
  protected String insertScopeVariablesAndGenerateExecutableJavaScript(
      Bindings compileScope, String sourceCode, String sourceName) {
    compileScope.put("babelScriptSource", sourceCode);

    // Build up the options to the BabelScript compiler.
    JsonObject opts = new JsonObject();
    opts.addProperty("nonStandard", true); // JSX+Flow
    opts.addProperty("filename", sourceName);

    String js =
        "(function() {" +
            "  try {" +
            "    return babel.transform(babelScriptSource, %s).code;" +
            "  } catch (e) {" +
            "    return {message: e.message}" +
            "  }" +
            "})();";
    return String.format(js, opts.toString());
  }

  @Override
  protected BabelScriptCompilerException generateExceptionFromMessage(String message) {
    return new BabelScriptCompilerException(message);
  }

  /**
   * @see org.plovr.CoffeeScriptCompiler.CoffeeScriptCompilerHolder
   */
  private static class BabelScriptCompilerHolder {
    private static BabelScriptCompiler instance = new BabelScriptCompiler();
  }
}