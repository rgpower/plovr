package org.plovr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;

/**
 * An exception thrown by the BabelScript compiler.
 *
 * @author rickp@celtx.com (Rick Power)
 */
public class BabelScriptCompilerException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  @VisibleForTesting
  static final Pattern BABEL_ERROR =
      Pattern.compile("([^:]+): (.*) \\((\\d+):(\\d+)\\)");

  private final int lineno;
  private final int charno;

  /**
   * @param message should be of the form:
   * FILENAME: SHORTMSG: (LINENO:CHARNO)
   */
  public BabelScriptCompilerException(String message) {
    super(message);

    Matcher matcher = BABEL_ERROR.matcher(message);
    if (matcher.find()) {
      lineno = Integer.valueOf(matcher.group(3), 10);
      charno = Integer.valueOf(matcher.group(4), 10);
    } else {
      lineno = -1;
      charno = -1;
    }
  }

  public int getLineNumber() {
    return lineno;
  }

  public int getCharNumber() {
    return charno;
  }
}
