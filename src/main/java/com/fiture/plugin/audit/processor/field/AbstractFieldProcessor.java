package com.fiture.plugin.audit.processor.field;

import com.fiture.plugin.audit.LombokBundle;
import com.fiture.plugin.audit.problem.LombokProblem;
import com.fiture.plugin.audit.problem.ProblemBuilder;
import com.fiture.plugin.audit.problem.ProblemEmptyBuilder;
import com.fiture.plugin.audit.problem.ProblemNewBuilder;
import com.fiture.plugin.audit.processor.AbstractProcessor;
import com.fiture.plugin.audit.util.LombokProcessorUtil;
import com.fiture.plugin.audit.util.PsiAnnotationSearchUtil;
import com.fiture.plugin.audit.util.PsiClassUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Base lombok processor class for field annotations
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractFieldProcessor extends AbstractProcessor implements FieldProcessor {

  AbstractFieldProcessor(@NotNull Class<? extends PsiElement> supportedClass,
                         @NotNull String supportedAnnotationClass) {
    super(supportedClass, supportedAnnotationClass);
  }

  AbstractFieldProcessor(@NotNull Class<? extends PsiElement> supportedClass,
                         @NotNull String supportedAnnotationClass,
                         @NotNull String equivalentAnnotationClass) {
    super(supportedClass, supportedAnnotationClass, equivalentAnnotationClass);
  }

  @NotNull
  @Override
  public List<? super PsiElement> process(@NotNull PsiClass psiClass, @Nullable String nameHint) {
    List<? super PsiElement> result = new ArrayList<>();
    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, getSupportedAnnotationClasses());
      if (null != psiAnnotation) {
        if (possibleToGenerateElementNamed(nameHint, psiClass, psiAnnotation, psiField)
            && validate(psiAnnotation, psiField, ProblemEmptyBuilder.getInstance())) {

          generatePsiElements(psiField, psiAnnotation, result);
        }
      }
    }
    return result;
  }

  protected boolean possibleToGenerateElementNamed(@Nullable String nameHint, @NotNull PsiClass psiClass,
                                                   @NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField) {
    return true;
  }

  @NotNull
  @Override
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    List<PsiAnnotation> result = new ArrayList<>();
    for (PsiField psiField : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
      PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiField, getSupportedAnnotationClasses());
      if (null != psiAnnotation) {
        result.add(psiAnnotation);
      }
    }
    return result;
  }

  @NotNull
  @Override
  public Collection<LombokProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation) {
    Collection<LombokProblem> result = Collections.emptyList();

    PsiField psiField = PsiTreeUtil.getParentOfType(psiAnnotation, PsiField.class);
    if (null != psiField) {
      ProblemNewBuilder problemNewBuilder = new ProblemNewBuilder();
      validate(psiAnnotation, psiField, problemNewBuilder);
      result = problemNewBuilder.getProblems();
    }

    return result;
  }

  protected abstract boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder);

  protected void validateOnXAnnotations(@NotNull PsiAnnotation psiAnnotation,
                                        @NotNull PsiField psiField,
                                        @NotNull ProblemBuilder builder,
                                        @NotNull String parameterName) {
    if (psiField.isDeprecated()) {
      final Iterable<String> onMethodAnnotations = LombokProcessorUtil.getOnX(psiAnnotation, "onMethod");
      if (StreamSupport.stream(onMethodAnnotations.spliterator(), false).anyMatch(CommonClassNames.JAVA_LANG_DEPRECATED::equals)) {
        builder.addError(LombokBundle.message("inspection.message.annotation.copy.duplicate", CommonClassNames.JAVA_LANG_DEPRECATED));
      }
    }
  }

  protected abstract void generatePsiElements(@NotNull PsiField psiField,
                                              @NotNull PsiAnnotation psiAnnotation,
                                              @NotNull List<? super PsiElement> target);

}
