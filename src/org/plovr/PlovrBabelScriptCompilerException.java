package org.plovr;

import com.google.common.base.Preconditions;

/**
 * {@link PlovrBabelScriptCompilerException} is a wrapper for a
 * {@link BabelScriptCompilerException} that contains information specific to
 * plovr, such as the {@link JsInput} that was responsible for the exception.
 *
 * @author rickp@celtx.com (Rick Power)
 */
public class PlovrBabelScriptCompilerException extends UncheckedCompilationException {

  private static final long serialVersionUID = 1L;

  private final BabelScriptCompilerException babelScriptCompilerException;
  private final JsInput input;

  public PlovrBabelScriptCompilerException(
      BabelScriptCompilerException cause, JsInput input) {
    super(cause);
    Preconditions.checkNotNull(cause);
    this.babelScriptCompilerException = cause;
    this.input = input;
  }

  public BabelScriptCompilerException getBabelScriptCompilerException() {
    return babelScriptCompilerException;
  }

  public JsInput getInput() {
    return input;
  }

  public int getLineNumber() {
    return babelScriptCompilerException.getLineNumber();
  }

  public int getCharNumber() {
    return babelScriptCompilerException.getCharNumber();
  }

  @Override public CompilationException toCheckedException() {
    return new CheckedBabelScriptCompilerException(this);
  }
}
