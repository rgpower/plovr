package org.plovr;

import com.google.common.base.Joiner;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Unit test for {@link BabelScriptCompiler}.
 *
 * @author rickp@celtx.com (Rick Power)
 */
public class BabelScriptCompilerTest {

  @Test
  public void testSimpleCompilation() throws BabelScriptCompilerException {
    String compiledBabelScript = BabelScriptCompiler.getInstance().compile(
        Joiner.on('\n').join(
            "var HelloWorld = React.createClass({",
            "  render: function() {",
            "  return (",
            "    <p>",
            "      Hello, <input type=\"text\" placeholder=\"Your name here\" />!",
            "      It is {this.props.date.toTimeString()}",
            "    </p>",
            "    );",
            "  }",
            "});"
            ),
        "helloworld.jsx");
    assertEquals(Joiner.on('\n').join(
        "\"use strict\";",
        "",
        "var HelloWorld = React.createClass({",
        "  displayName: \"HelloWorld\",",
        "",
        "  render: function render() {",
        "    return React.createElement(",
        "      \"p\",",
        "      null,",
        "      \"Hello, \",",
        "      React.createElement(\"input\", { type: \"text\", placeholder: \"Your name here\" }),",
        "      \"! It is \",",
        "      this.props.date.toTimeString()",
        "    );",
        "  }",
        "});"
        ),
        compiledBabelScript);
  }

  @Test
  public void testSimpleCompilationError() {
    BabelScriptCompiler compiler = BabelScriptCompiler.getInstance();
    try {
      compiler.compile("var HelloWorld = React.createClass(", "helloworld.jsx");
      fail("Should throw BabelScriptCompilerException");
    } catch (BabelScriptCompilerException e) {
      assertEquals(Joiner.on('\n').join(
          "helloworld.jsx: Unexpected token (1:35)",
          "> 1 | var HelloWorld = React.createClass(",
          "    |                                    ^"
          ),
          e.getMessage());
    }
  }
}
