package org.plovr;

import java.io.File;
import java.io.IOException;

import org.plovr.io.Files;

/**
 * {@link JSXFile} represents a JSX source file on disk.
 *
 * @author rickp@celtx.com (Rick Power)
 */
public class JSXFile extends LocalFileJsInput {

  JSXFile(String name, File source) {
    super(name, source);
  }

  /**
   * @throws PlovrBabelScriptCompilerException if the BabelScript compiler
   *     encounters an error trying to compile the source
   */
  @Override
  public String generateCode() {
    try {
      return BabelScriptCompiler.getInstance().compile(
          Files.toString(getSource()), getName());
    } catch (BabelScriptCompilerException e) {
      throw new PlovrBabelScriptCompilerException(e, this);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
