package com.fiture.plugin.audit.processor.clazz;

import com.fiture.plugin.audit.LombokBundle;
import com.fiture.plugin.audit.LombokClassNames;
import com.fiture.plugin.audit.problem.ProblemBuilder;
import com.fiture.plugin.audit.processor.LombokPsiElementUsage;
import com.fiture.plugin.audit.processor.field.SetterFieldProcessor;
import com.fiture.plugin.audit.util.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Inspect and validate @Setter lombok annotation on a class
 * Creates setter methods for fields of this class
 *
 * @author Plushnikov Michail
 */
public class SetterProcessor extends AbstractClassProcessor {

  public SetterProcessor() {
    super(PsiMethod.class, LombokClassNames.SETTER);
  }

  private SetterFieldProcessor getSetterFieldProcessor() {
    return ApplicationManager.getApplication().getService(SetterFieldProcessor.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return validateAnnotationOnRightType(psiAnnotation, psiClass, builder) && validateVisibility(psiAnnotation);
  }

  private boolean validateAnnotationOnRightType(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError(LombokBundle.message("inspection.message.s.only.supported.on.class.or.field.type"), psiAnnotation.getQualifiedName());
      result = false;
    }
    return result;
  }

  private boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    return null != methodVisibility;
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodModifier(psiAnnotation);
    if (methodVisibility != null) {
      target.addAll(createFieldSetters(psiClass, methodVisibility));
    }
  }

  public Collection<PsiMethod> createFieldSetters(@NotNull PsiClass psiClass, @NotNull String methodModifier) {
    Collection<PsiMethod> result = new ArrayList<>();

    final Collection<PsiField> setterFields = filterSetterFields(psiClass);

    SetterFieldProcessor fieldProcessor = getSetterFieldProcessor();
    for (PsiField setterField : setterFields) {
      result.add(fieldProcessor.createSetterMethod(setterField, psiClass, methodModifier));
    }
    return result;
  }

  @NotNull
  private Collection<PsiField> filterSetterFields(@NotNull PsiClass psiClass) {
    final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    filterToleratedElements(classMethods);

    SetterFieldProcessor fieldProcessor = getSetterFieldProcessor();
    final Collection<PsiField> setterFields = new ArrayList<>();
    for (PsiField psiField : psiClass.getFields()) {
      boolean createSetter = true;
      PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        //Skip final fields.
        createSetter = !modifierList.hasModifierProperty(PsiModifier.FINAL);
        //Skip static fields.
        createSetter &= !modifierList.hasModifierProperty(PsiModifier.STATIC);
        //Skip fields having Setter annotation already
        createSetter &= PsiAnnotationSearchUtil.isNotAnnotatedWith(psiField, fieldProcessor.getSupportedAnnotationClasses());
        //Skip fields that start with $
        createSetter &= !psiField.getName().startsWith(LombokUtils.LOMBOK_INTERN_FIELD_MARKER);
        //Skip fields if a method with same name already exists
        final Collection<String> methodNames = fieldProcessor.getAllSetterNames(psiField, PsiType.BOOLEAN.equals(psiField.getType()));
        for (String methodName : methodNames) {
          createSetter &= !PsiMethodUtil.hasSimilarMethod(classMethods, methodName, 1);
        }
      }
      if (createSetter) {
        setterFields.add(psiField);
      }
    }
    return setterFields;
  }

  @Override
  public LombokPsiElementUsage checkFieldUsage(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation) {
    final PsiClass containingClass = psiField.getContainingClass();
    if (null != containingClass) {
      if (PsiClassUtil.getNames(filterSetterFields(containingClass)).contains(psiField.getName())) {
        return LombokPsiElementUsage.WRITE;
      }
    }
    return LombokPsiElementUsage.NONE;
  }
}
