package com.fiture.plugin.audit.processor;

import com.fiture.plugin.audit.LombokClassNames;
import com.fiture.plugin.audit.util.LombokProcessorUtil;
import com.fiture.plugin.audit.util.PsiAnnotationSearchUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Base lombok processor class
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractProcessor implements Processor {
  /**
   * Annotation classes this processor supports
   */
  private final String[] supportedAnnotationClasses;
  /**
   * Kind of output elements this processor supports
   */
  private final Class<? extends PsiElement> supportedClass;

  /**
   * Constructor for all Lombok-Processors
   *
   * @param supportedClass             kind of output elements this processor supports
   * @param supportedAnnotationClasses annotations this processor supports
   */
  protected AbstractProcessor(@NotNull Class<? extends PsiElement> supportedClass,
                              @NotNull String... supportedAnnotationClasses) {
    this.supportedClass = supportedClass;
    this.supportedAnnotationClasses = supportedAnnotationClasses;
  }

  @Override
  public final @NotNull String @NotNull [] getSupportedAnnotationClasses() {
    return supportedAnnotationClasses;
  }

  @NotNull
  @Override
  public final Class<? extends PsiElement> getSupportedClass() {
    return supportedClass;
  }

  @NotNull
  public abstract Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass);

  protected boolean supportAnnotationVariant(@NotNull PsiAnnotation psiAnnotation) {
    return true;
  }

  protected void filterToleratedElements(@NotNull Collection<? extends PsiModifierListOwner> definedMethods) {
    definedMethods.removeIf(definedMethod -> PsiAnnotationSearchUtil.isAnnotatedWith(definedMethod, LombokClassNames.TOLERATE));
  }

  protected static void copyOnXAnnotations(@Nullable PsiAnnotation processedAnnotation,
                                           @NotNull PsiModifierList modifierList,
                                           @NotNull String onXParameterName) {
    if (processedAnnotation == null) {
      return;
    }

    Iterable<String> annotationsToAdd = LombokProcessorUtil.getOnX(processedAnnotation, onXParameterName);
    annotationsToAdd.forEach(modifierList::addAnnotation);
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    return LombokPsiElementUsage.NONE;
  }
}
