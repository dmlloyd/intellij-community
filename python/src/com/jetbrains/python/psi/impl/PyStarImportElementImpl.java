package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.toolbox.ChainIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author dcheryasov
 */
public class PyStarImportElementImpl extends PyElementImpl implements PyStarImportElement {

  public PyStarImportElementImpl(ASTNode astNode) {
    super(astNode);
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    if (getParent() instanceof PyFromImportStatement) {
      PyFromImportStatement fromImportStatement = (PyFromImportStatement)getParent();
      final List<PsiElement> importedFiles = ResolveImportUtil.resolveFromOrForeignImport(fromImportStatement,
                                                                                          fromImportStatement.getImportSourceQName());
      ChainIterable<PyElement> chain = new ChainIterable<PyElement>();
      for (PsiElement importedFile : new HashSet<PsiElement>(importedFiles)) { // resolver gives lots of duplicates
        final PsiElement source = PyUtil.turnDirIntoInit(importedFile);
        if (source instanceof PyFile) {
          chain.add(((PyFile) source).iterateNames());
        }
      }
      return chain;
    }
    return Collections.emptyList();
  }

  @Nullable
  public PsiElement getElementNamed(final String name) {
    if (PyUtil.isClassPrivateName(name)) {
      return null;
    }
    if (getParent() instanceof PyFromImportStatement) {
      PyFromImportStatement fromImportStatement = (PyFromImportStatement)getParent();
      final List<PsiElement> importedFiles = ResolveImportUtil.resolveFromOrForeignImport(fromImportStatement,
                                                                                          fromImportStatement.getImportSourceQName());
      for (PsiElement importedFile : new HashSet<PsiElement>(importedFiles)) { // resolver gives lots of duplicates
        final PsiElement source = PyUtil.turnDirIntoInit(importedFile);
        if (source instanceof PyFile) {
          PyFile sourceFile = (PyFile)source;
          final PsiElement exportedName = sourceFile.getElementNamed(name);
          if (exportedName != null) {
            final List<String> all = sourceFile.getDunderAll();
            if (all != null && !all.contains(name)) {
              continue;
            }
            return exportedName;
          }
        }
      }
    }
    return null;
  }

  public boolean mustResolveOutside() {
    return true; // we don't have children, but... 
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {

      private String getName() {
        PyFromImportStatement elt = PsiTreeUtil.getParentOfType(PyStarImportElementImpl.this, PyFromImportStatement.class);
        if (elt != null) { // always? who knows :)
          PyReferenceExpression imp_src = elt.getImportSource();
          if (imp_src != null) {
            return PyResolveUtil.toPath(imp_src);
          }
        }
        return "<?>";
      }

      public String getPresentableText() {
        return getName();
      }

      public String getLocationString() {
        StringBuilder buf = new StringBuilder("| ");
        buf.append("from ").append(getName()).append(" import *");
        return buf.toString();
      }

      public Icon getIcon(final boolean open) {
        return null;
      }
    };
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyStarImportElement(this);
  }
}
