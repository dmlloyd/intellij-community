/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLoopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.ResolveKind.*;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ven
 */
public class ResolveUtil {
  public static boolean treeWalkUp(PsiElement place, PsiScopeProcessor processor) {
    PsiElement lastParent = null;
    PsiElement run = place;
    while (run != null) {
      if (!run.processDeclarations(processor, PsiSubstitutor.EMPTY, lastParent, place)) return false;
      lastParent = run;
      run = run.getParent();
    }

    return true;
  }
                                  
  public static boolean processChildren(PsiElement element, PsiScopeProcessor processor,
                                        PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    PsiElement run = lastParent == null ? element.getLastChild() : lastParent.getPrevSibling();
    while(run != null) {
      if (!run.processDeclarations(processor, substitutor, null, place)) return false;
      run = run.getPrevSibling();
    }

    return true;
  }

  public static boolean processElement(PsiScopeProcessor processor, PsiNamedElement namedElement) {
    NameHint nameHint = processor.getHint(NameHint.class);
    String name = nameHint == null ? null : nameHint.getName();
    if (name == null || name.equals(namedElement.getName())) {
      return processor.execute(namedElement, PsiSubstitutor.EMPTY);
    }

    return true;
  }

  public static ClassHint.ResolveKind getResolveKind(PsiElement element) {
    if (element instanceof PsiVariable) return PROPERTY;
    if (element instanceof GrReferenceExpression) return PROPERTY;

    else if (element instanceof PsiMethod) return  METHOD;

    else return CLASS_OR_PACKAGE;
  }

  public static PsiElement[] mapToElements(GroovyResolveResult[] candidates) {
    PsiElement[] elements = new PsiElement[candidates.length];
    for (int i = 0; i < elements.length; i++) {
      elements[i] = candidates[i].getElement();
    }

    return elements;
  }

  public static boolean isSuperMethodDominated(PsiMethod method, List<PsiMethod> worklist) {
    PsiParameter[] params = method.getParameterList().getParameters();
    PsiModifierList modifierList = method.getModifierList();

    NextMethod:
    for (PsiMethod other : worklist) {
      PsiParameter[] otherParams = other.getParameterList().getParameters();
      if (otherParams.length != params.length) continue;
      if (PsiUtil.getAccessLevel(other.getModifierList()) > PsiUtil.getAccessLevel(modifierList)) continue;
      for (int i = 0; i < params.length; i++) {
        PsiType type = TypeConversionUtil.erasure(params[i].getType());
        PsiType otherType = TypeConversionUtil.erasure(otherParams[i].getType());
        if (!type.isAssignableFrom(otherType)) continue NextMethod;
      }
      return true;
    }

    return false;
  }

  public static boolean processDefaultMethods(PsiType type, PsiScopeProcessor processor, Project project) {
    return processDefaultMethods(type, processor, project, new HashSet<String>());
  }

  private static boolean processDefaultMethods(PsiType type, PsiScopeProcessor processor, Project project,  Set<String> visited) {
    String qName = rawCanonicalText(type);

    if (qName != null) {
      if (visited.contains(qName)) return true;
      visited.add(qName);
      List<PsiMethod> defaultMethods = GroovyPsiManager.getInstance(project).getDefaultMethods(qName);
      for (PsiMethod defaultMethod : defaultMethods) {
        if (!processElement(processor, defaultMethod)) return false;
      }

      for (PsiType superType : type.getSuperTypes()) {
        processDefaultMethods(TypeConversionUtil.erasure(superType), processor, project, visited);
      }
    }

    return true;
  }

  private static String rawCanonicalText(PsiType type) {
    final String result = type.getCanonicalText();
    if (result == null) return null;
    final int i = result.indexOf('<');
    if (i > 0) return result.substring(0, i);
    return result;
  }

  public static PsiType getListTypeForSpreadOperator(GrReferenceExpression refExpr, PsiType componentType) {
    PsiClass clazz = findListClass(refExpr.getManager(), refExpr.getResolveScope());
    if (clazz != null) {
      PsiTypeParameter[] typeParameters = clazz.getTypeParameters();
      if (typeParameters.length == 1) {
        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY.put(typeParameters[0], componentType);
        return refExpr.getManager().getElementFactory().createType(clazz, substitutor);
      }
    }

    return null;
  }

  public static PsiClass findListClass(PsiManager manager, GlobalSearchScope resolveScope) {
      return manager.findClass("java.util.List", resolveScope);
  }

  public static GrVariable resolveDuplicateLocalVariable(GrVariable variable) {
    ResolverProcessor processor = new ResolverProcessor(variable.getName(), EnumSet.of(PROPERTY, METHOD), variable, false, PsiType.EMPTY_ARRAY);
    treeWalkUp(variable, processor);
    final GroovyResolveResult[] candidates = processor.getCandidates();
    for (GroovyResolveResult candidate : candidates) {
      final PsiElement element = candidate.getElement();
      if (element == variable) continue;
      if (element instanceof GrVariable) return (GrVariable) element;
    }

    return null;
  }

  public static GrLabeledStatement resolveLabeledStatement(String label, PsiElement place) {
    while (place != null) {
      /*PsiElement run = place;
      while (run != null) {
        if (run instanceof GrLabeledStatement && label.equals(((GrLabeledStatement) run).getLabel()))
          return (GrLabeledStatement) run;

        run = run.getPrevSibling();
      }*/
      final PsiElement parent = place.getContext();

      if (place instanceof GrLoopStatement && parent instanceof GrLabeledStatement) {
        GrLabeledStatement labeledStatement = (GrLabeledStatement) parent;
        if (label.equals(labeledStatement.getLabel())) {
          return labeledStatement;
        }
      }
      place = parent;

      if (place instanceof GrMember) break;
    }
    return null;
  }
}
