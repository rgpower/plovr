package org.plovr;

import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.DiagnosticType;
import com.google.javascript.jscomp.JSError;

/**
 * {@link PlovrBabelScriptCompilerException} is a BabelScript exception that
 * can generate a {@link JSError} that can be displayed for the user.
 *
 * @author rickp@celtx.com (Rick Power)
 */
public class CheckedBabelScriptCompilerException extends CompilationException.Single {

  private static final long serialVersionUID = 1L;

  private final PlovrBabelScriptCompilerException babelScriptException;

  private static final DiagnosticType BABEL_SCRIPT_SYNTAX_EXCEPTION =
      DiagnosticType.error("BABEL_SCRIPT_SYNTAX_EXCEPTION", "{0}");

  public CheckedBabelScriptCompilerException(
      PlovrBabelScriptCompilerException cause) {
    super(cause);
    Preconditions.checkNotNull(cause);
    this.babelScriptException = cause;
  }

  @Override
  public CompilationError createCompilationError() {
    int lineno = babelScriptException.getLineNumber();
    int charno = babelScriptException.getCharNumber();
    JSError jsError = JSError.make(
        babelScriptException.getInput().getName(),
        lineno,
        charno,
        CheckLevel.ERROR,
        BABEL_SCRIPT_SYNTAX_EXCEPTION,
        getMessage());
    return new CompilationError(jsError);
  }
}
