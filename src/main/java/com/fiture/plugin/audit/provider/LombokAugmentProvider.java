package com.fiture.plugin.audit.provider;

import com.fiture.plugin.audit.processor.Processor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Provides support for lombok generated elements
 *
 * @author Plushnikov Michail
 */
public class LombokAugmentProvider extends PsiAugmentProvider {
  private static final Logger log = Logger.getInstance(LombokAugmentProvider.class.getName());

  public LombokAugmentProvider() {
    log.debug("LombokAugmentProvider created");
  }

  @NotNull
  @Override
  public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                        @NotNull final Class<Psi> type) {
    return getAugments(element, type, null);
  }

  @NotNull
  @Override
  public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                        @NotNull final Class<Psi> type,
                                                        @Nullable String nameHint) {
    final List<Psi> emptyResult = Collections.emptyList();
    if ((type != PsiClass.class && type != PsiField.class && type != PsiMethod.class) || !(element instanceof PsiExtensibleClass)) {
      return emptyResult;
    }

    final PsiClass psiClass = (PsiClass) element;
    // Skip processing of Annotations and Interfaces
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
      return emptyResult;
    }
    // skip processing if disabled, or no lombok library is present
//    if (!LombokLibraryUtil.hasLombokLibrary(element.getProject())) {
//      return emptyResult;
//    }

    // All invoker of AugmentProvider already make caching
    // and we want to try to skip recursive calls completely

///      final String message = String.format("Process call for type: %s class: %s", type.getSimpleName(), psiClass.getQualifiedName());
//      log.info(">>>" + message);
    final List<Psi> result = getPsis(psiClass, type, nameHint);
//      log.info("<<<" + message);
    return result;
  }

  @NotNull
  private static <Psi extends PsiElement> List<Psi> getPsis(PsiClass psiClass, Class<Psi> type, String nameHint) {
    final List<Psi> result = new ArrayList<>();
    final Collection<Processor> lombokProcessors = LombokProcessorProvider.getInstance(psiClass.getProject()).getLombokProcessors(type);
    for (Processor processor : lombokProcessors) {
      if (processor.notNameHintIsEqualToSupportedAnnotation(nameHint)) {
        final List<? super PsiElement> generatedElements = processor.process(psiClass, nameHint);
        for (Object psiElement : generatedElements) {
          result.add((Psi) psiElement);
        }
      }
    }
    return result;
  }
}
