/*
 * Copyright 2015 The Closure Compiler Authors.
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
package com.google.javascript.jscomp;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.javascript.jscomp.FunctionInjector.InliningMode;
import com.google.javascript.jscomp.FunctionInjector.Reference;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites/inlines some J2CL constructs to be more optimizable.
 *
 * <p>Inlines Arrays.$create(), Arrays.$init(), Arrays.$instanceIsOfType(), Arrays.$castTo() and
 * Casts.to() so that all references to Object.$isInstance() functions will be fully qualified
 * and easy to strip.
 */
public class J2clPass implements CompilerPass {

  private final AbstractCompiler compiler;
  private final Supplier<String> safeNameIdSupplier;

  /**
   * Collects references to certain function definitions in a certain class and then inlines fully
   * qualified static method calls to those functions anywhere in the program.
   *
   * <p>Assumes that the set of provided short function names will not collide with any of the
   * collected fully qualified function names once the module prefix has been added.
   */
  private class ClassStaticFunctionsInliner {

    private class FunctionDefsCollector implements Callback {
      @Override
      public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
        // Only look inside the referenced class file.
        return !n.isScript() || n.getSourceFileName().endsWith(classFileName);
      }

      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        // If we arrive here then we're already inside the desired script.

        // Only look at named function declarations
        if (!n.isAssign() || !n.getLastChild().isFunction()) {
          return;
        }

        // ... that are fully qualified
        Node qualifiedNameNode = n.getFirstChild();
        if (!qualifiedNameNode.isGetProp() || !qualifiedNameNode.isQualifiedName()) {
          return;
        }

        Node fnNode = n.getLastChild();
        String qualifiedFnName = qualifiedNameNode.getQualifiedName();
        String fnName = qualifiedNameNode.getLastChild().getString();
        if (fnNamesToInline.contains(fnName)) {
          // Then store a reference to it.
          fnsToInlineByQualifiedName.put(qualifiedFnName, fnNode);
        }
      }
    }

    private class StaticCallInliner extends AbstractPostOrderCallback {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        // Only look at method calls
        if (!n.isCall()) {
          return;
        }

        // ... that are fully qualified
        Node qualifiedNameNode = n.getFirstChild();
        if (!qualifiedNameNode.isGetProp() || !qualifiedNameNode.isQualifiedName()) {
          return;
        }

        // ... and that reference a function definition we want to inline
        String qualifiedFnName = qualifiedNameNode.getQualifiedName();
        String fnName = qualifiedNameNode.getLastChild().getString();
        Node fnImpl = fnsToInlineByQualifiedName.get(qualifiedFnName);
        if (fnImpl == null) {
          return;
        }

        // Otherwise inline the call.
        Node inlinedCall =
            injector.inline(
                new Reference(n, t.getScope(), t.getModule(), InliningMode.DIRECT), fnName, fnImpl);
        t.getCompiler().reportChangeToEnclosingScope(inlinedCall);
      }
    }

    private final String classFileName;
    private final Set<String> fnNamesToInline;
    private final Map<String, Node> fnsToInlineByQualifiedName = new HashMap<>();
    private final FunctionInjector injector;
    private final Node root;

    private ClassStaticFunctionsInliner(
        Node root, String classFileName, Set<String> fnNamesToInline) {
      this.root = root;
      this.classFileName = classFileName;
      this.fnNamesToInline = fnNamesToInline;

      this.injector = new FunctionInjector(compiler, safeNameIdSupplier, true, true, true);
      this.injector.setKnownConstants(fnNamesToInline);
    }

    private void run() {
      NodeTraversal.traverseEs6(compiler, root, new FunctionDefsCollector());
      NodeTraversal.traverseEs6(compiler, root, new StaticCallInliner());
    }
  }

  public J2clPass(AbstractCompiler compiler) {
    this.compiler = compiler;
    this.safeNameIdSupplier = compiler.getUniqueNameIdSupplier();
  }

  @Override
  public void process(Node externs, Node root) {
    inlineFunctionsInFile(
        root,
        "j2cl/transpiler/vmbootstrap/Arrays.impl.js",
        ImmutableSet.of("$create", "$init", "$instanceIsOfType", "$castTo"));
    inlineFunctionsInFile(root, "j2cl/transpiler/vmbootstrap/Casts.impl.js", ImmutableSet.of("to"));
  }

  private void inlineFunctionsInFile(Node root, String classFileName, Set<String> fnNamesToInline) {
    new ClassStaticFunctionsInliner(root, classFileName, fnNamesToInline).run();
  }
}