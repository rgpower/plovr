/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.jssrc.internal;

import com.google.template.soy.shared.internal.AbstractGenerateSoyEscapingDirectiveCode;
import com.google.template.soy.shared.internal.DirectiveDigest;
import com.google.template.soy.shared.restricted.EscapingConventions;
import com.google.template.soy.shared.restricted.EscapingConventions.EscapingLanguage;
import com.google.template.soy.shared.restricted.Sanitizers;
import com.google.template.soy.shared.restricted.TagWhitelist;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.annotation.ParametersAreNonnullByDefault;


/**
 * Generates JavaScript code relied upon by soyutils.js and soyutils_use_goog.js.
 *
 * <p>
 * This is an ant task and can be invoked as:
 * <xmp>
 *   <taskdef name="gen.escape.directives"
 *    classname="com.google.template.soy.jssrc.internal.GenerateSoyUtilsEscapingDirectiveCode">
 *     <classpath>
 *       <!-- classpath to Soy classes and dependencies -->
 *     </classpath>
 *   </taskdef>
 *   <gen.escape.directives>
 *     <input path="one or more JS files that use the generated helpers"/>
 *     <output path="the output JS file"/>
 *     <libdefined pattern="goog.*"/>  <!-- enables closure alternatives -->
 *   </gen.escape.directives>
 * </xmp>
 *
 * <p>
 * In the above, the first {@code <taskdef>} is an Ant builtin which links the element named
 * {@code <gen.escape.directives>} to this class.
 * <p>
 * That element contains zero or more {@code <input>}s which are JavaScript source files that may
 * use the helper functions generated by this task.
 * <p>
 * There must be exactly one {@code <output>} element which specifies where the output should be
 * written.  That output contains the input sources and the generated helper functions.
 * <p>
 * There may be zero or more {@code <libdefined>} elements which specify which functions should be
 * available in the context in which {@code <output>} is run.
 *
 */
@ParametersAreNonnullByDefault
public final class GenerateSoyUtilsEscapingDirectiveCode
    extends AbstractGenerateSoyEscapingDirectiveCode {

  @Override protected EscapingLanguage getLanguage() {
    return EscapingLanguage.JAVASCRIPT;
  }

  @Override protected String getLineCommentSyntax() {
    return "//";
  }

  @Override protected String getLineEndSyntax() {
    return ";";
  }

  @Override protected String getRegexStart() {
    return "/";
  }

  @Override protected String getRegexEnd() {
    return "/g";
  }

  @Override protected String escapeOutputString(String input) {
    return EscapingConventions.EscapeJsString.INSTANCE.escape(input);
  }

  @Override protected String convertFromJavaRegex(Pattern javaPattern) {
    String body = javaPattern.pattern()
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
        .replace("\u0000", "\\u0000")
        .replace("\u0020", "\\u0020")
        .replace("\u2028", "\\u2028")
        .replace("\u2029", "\\u2029")
        .replace("\\A", "^")
        .replace("\\z", "$")
        .replaceAll("(?<!\\\\)(?:\\\\{2})*/", "\\\\/");
    // Some features supported by Java are not supported by JavaScript such as lookbehind,
    // DOTALL, and unicode character classes.
    if (body.contains("(?<")) {
      throw new IllegalArgumentException("Pattern " + javaPattern + " uses lookbehind.");
    } else if ((javaPattern.flags() & Pattern.DOTALL) != 0) {
      throw new IllegalArgumentException("Pattern " + javaPattern + " uses DOTALL.");
    } else if (NAMED_CLASS.matcher(body).find()) {
      throw new IllegalArgumentException("Pattern " + javaPattern +
          " uses named characer classes.");
    }

    StringBuilder buffer = new StringBuilder(body.length() + 4);
    buffer.append('/').append(body).append('/');
    if ((javaPattern.flags() & Pattern.CASE_INSENSITIVE) != 0) {
      buffer.append('i');
    }
    if ((javaPattern.flags() & Pattern.MULTILINE) != 0) {
      buffer.append('m');
    }
    return buffer.toString();
  }

  @Override protected void generateCharacterMapSignature(StringBuilder outputCode, String mapName) {
    outputCode
        .append('\n')
        .append("/**\n")
        .append(" * Maps characters to the escaped versions for the named escape directives.\n")
        .append(" * @private {!Object<string, string>}\n")
        .append(" */\n")
        .append("soy.esc.$$ESCAPE_MAP_FOR_").append(mapName).append("_");
  }

  @Override protected void generateMatcher(StringBuilder outputCode, String name, String matcher) {
    outputCode
        .append('\n')
        .append("/**\n")
        .append(" * Matches characters that need to be escaped for the named directives.\n")
        .append(" * @private {!RegExp}\n")
        .append(" */\n")
        .append("soy.esc.$$MATCHER_FOR_").append(name).append("_ = ").append(matcher)
        .append(";\n");
  }

  @Override protected void generateFilter(StringBuilder outputCode, String name, String filter) {
    outputCode
        .append('\n')
        .append("/**\n")
        .append(" * A pattern that vets values produced by the named directives.\n")
        .append(" * @private {!RegExp}\n")
        .append(" */\n")
        .append("soy.esc.$$FILTER_FOR_").append(name).append("_ = ").append(filter)
        .append(";\n");
  }

  @Override protected void generateCommonConstants(StringBuilder outputCode) {
    // Emit patterns and constants needed by escaping functions that are not part of any one
    // escaping convention.
    outputCode.append('\n')
        .append("/**\n")
        .append(" * Matches all tags, HTML comments, and DOCTYPEs in tag soup HTML.\n")
        .append(" * By removing these, and replacing any '<' or '>' characters with\n")
        .append(" * entities we guarantee that the result can be embedded into a\n")
        .append(" * an attribute without introducing a tag boundary.\n")
        .append(" *\n")
        .append(" * @private {!RegExp}\n")
        .append(" */\n")
        .append("soy.esc.$$HTML_TAG_REGEX_ = ")
        .append(convertFromJavaRegex(EscapingConventions.HTML_TAG_CONTENT))
        .append("g;\n");

    outputCode.append("\n")
        .append("/**\n")
        .append(" * Matches all occurrences of '<'.\n")
        .append(" *\n")
        .append(" * @private {!RegExp}\n")
        .append(" */\n")
        .append("soy.esc.$$LT_REGEX_ = /</g;\n");

    outputCode.append('\n')
        .append("/**\n")
        .append(" * Maps lower-case names of innocuous tags to true.\n")
        .append(" *\n")
        .append(" * @private {!Object<string, boolean>}\n")
        .append(" */\n")
        .append("soy.esc.$$SAFE_TAG_WHITELIST_ = ")
        .append(toJsStringSet(TagWhitelist.FORMATTING.asSet()))
        .append(";\n");

    outputCode.append('\n')
        .append("/**\n")
        .append(" * Pattern for matching attribute name and value, where value is single-quoted\n")
        .append(" * or double-quoted.\n")
        .append(" * See http://www.w3.org/TR/2011/WD-html5-20110525/syntax.html#attributes-0\n")
        .append(" *\n")
        .append(" * @private {!RegExp}\n")
        .append(" */\n")
        .append("soy.esc.$$HTML_ATTRIBUTE_REGEX_ = ")
        .append(convertFromJavaRegex(Sanitizers.HTML_ATTRIBUTE_PATTERN))
        .append("g;\n");
  }

  @Override protected void generateReplacerFunction(StringBuilder outputCode, String mapName) {
    outputCode
        .append('\n')
        .append("/**\n")
        .append(" * A function that can be used with String.replace.\n")
        .append(" * @param {string} ch A single character matched by a compatible matcher.\n")
        .append(" * @return {string} A token in the output language.\n")
        .append(" * @private\n")
        .append(" */\n")
        .append("soy.esc.$$REPLACER_FOR_")
        .append(mapName)
        .append("_ = function(ch) {\n")
        .append("  return soy.esc.$$ESCAPE_MAP_FOR_").append(mapName).append("_[ch];\n")
        .append("};\n");
  }

  @Override protected void useExistingLibraryFunction(StringBuilder outputCode, String identifier,
      String existingFunction) {
    outputCode
        .append('\n')
        .append("/**\n")
        .append(" * @type {function (*) : string}\n")
        .append(" */\n")
        .append("soy.esc.$$").append(identifier).append("Helper = function(v) {\n")
        .append("  return ").append(existingFunction).append("(String(v));\n")
        .append("};\n");
  }

  @Override protected void generateHelperFunction(StringBuilder outputCode,
      DirectiveDigest digest) {
    String name = digest.getDirectiveName();
    outputCode
        .append('\n')
        .append("/**\n")
        .append(" * A helper for the Soy directive |").append(name).append('\n')
        .append(" * @param {*} value Can be of any type but will be coerced to a string.\n")
        .append(" * @return {string} The escaped text.\n")
        .append(" */\n")
        .append("soy.esc.$$").append(name).append("Helper = function(value) {\n")
        .append("  var str = String(value);\n");
    if (digest.getFilterName() != null) {
      String filterName = digest.getFilterName();
      outputCode
          .append("  if (!soy.esc.$$FILTER_FOR_").append(filterName).append("_.test(str)) {\n");
      if (availableIdentifiers.apply("goog.asserts.fail")) {
        outputCode
            .append("    goog.asserts.fail('Bad value `%s` for |").append(name)
            .append("', [str]);\n");
      }
      outputCode
          .append("    return '").append(digest.getInnocuousOutput()).append("';\n")
          .append("  }\n");
    }

    if (digest.getNonAsciiPrefix() != null) {
      // TODO(msamuel): We can add a second replace of all non-ascii codepoints below.
      throw new UnsupportedOperationException("Non ASCII prefix escapers not implemented yet.");
    }
    if (digest.getEscapesName() != null) {
      String escapeMapName = digest.getEscapesName();
      String matcherName = digest.getMatcherName();
      outputCode
          .append("  return str.replace(\n")
          .append("      soy.esc.$$MATCHER_FOR_").append(matcherName).append("_,\n")
          .append("      soy.esc.$$REPLACER_FOR_").append(escapeMapName).append("_);\n");
    } else {
      outputCode.append("  return str;\n");
    }
    outputCode.append("};\n");
  }

  /** ["foo", "bar"] -> '{"foo": true, "bar": true}' */
  private String toJsStringSet(Iterable<String> strings) {
    StringBuilder sb = new StringBuilder();
    boolean isFirst = true;
    sb.append('{');
    for (String str : strings) {
      if (!isFirst) { sb.append(", "); }
      isFirst = false;
      writeStringLiteral(str, sb);
      sb.append(": true");
    }
    sb.append('}');
    return sb.toString();
  }

  /** Matches named character classes in Java regular expressions. */
  private static final Pattern NAMED_CLASS = Pattern.compile("(?<!\\\\)(\\\\{2})*\\\\p\\{");

  /**
   * A non Ant interface for this class.
   */
  public static void main(String[] args) throws IOException {
    GenerateSoyUtilsEscapingDirectiveCode generator = new GenerateSoyUtilsEscapingDirectiveCode();
    generator.configure(args);
    generator.execute();
  }
}
