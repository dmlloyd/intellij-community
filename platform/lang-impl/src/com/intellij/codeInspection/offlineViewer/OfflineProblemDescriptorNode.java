/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.codeInspection.offlineViewer;

import com.intellij.codeInspection.ProblemDescriptorUtil;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.codeInspection.ui.ProblemDescriptionNode;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class OfflineProblemDescriptorNode extends ProblemDescriptionNode {
  private final OfflineDescriptorResolveResult myDescriptorResolveResult;

  private OfflineProblemDescriptorNode(OfflineDescriptorResolveResult descriptorResolveResult,
                                       @NotNull InspectionToolPresentation presentation,
                                       @NotNull OfflineProblemDescriptor offlineDescriptor) {
    super(descriptorResolveResult.getResolvedEntity(), descriptorResolveResult.getResolvedDescriptor(), presentation, offlineDescriptor::getLine);
    myDescriptorResolveResult = descriptorResolveResult;
    if (descriptorResolveResult.getResolvedDescriptor() == null) {
      setUserObject(offlineDescriptor);
    }
  }

  static OfflineProblemDescriptorNode create(@NotNull OfflineProblemDescriptor offlineDescriptor,
                                             @NotNull OfflineDescriptorResolveResult resolveResult,
                                             @NotNull InspectionToolPresentation presentation) {
    return new OfflineProblemDescriptorNode(resolveResult,
                                            presentation,
                                            offlineDescriptor);
  }

  @NotNull
  @Override
  protected String calculatePresentableName() {
    String presentableName = super.calculatePresentableName();
    return presentableName.isEmpty() && getUserObject() instanceof OfflineProblemDescriptor
           ? ProblemDescriptorUtil.unescapeTags(StringUtil.notNullize(((OfflineProblemDescriptor)getUserObject()).getDescription())).trim()
           : presentableName;
  }

  @Override
  protected boolean calculateIsValid() {
    return false;
  }

  @Override
  public void excludeElement() {
    myDescriptorResolveResult.setExcluded(true);
  }

  @Override
  public void amnestyElement() {
    myDescriptorResolveResult.setExcluded(false);
  }

  @Override
  public boolean isExcluded() {
    return myDescriptorResolveResult.isExcluded();
  }
}
