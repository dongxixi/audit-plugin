package com.fiture.plugin.audit.usage;

import com.fiture.plugin.audit.psi.LombokLightFieldBuilder;
import com.fiture.plugin.audit.psi.LombokLightMethodBuilder;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * It should find calls to getters/setters of some field changed by lombok accessors
 */
public class AuditFieldFindUsagesHandlerFactory extends FindUsagesHandlerFactory {

  public AuditFieldFindUsagesHandlerFactory() {
  }

  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    if (element instanceof PsiField && !DumbService.isDumb(element.getProject())) {
      final PsiField psiField = (PsiField) element;
      final PsiClass containingClass = psiField.getContainingClass();
      if (containingClass != null) {
        return containingClass.hasAnnotation("com.fiture.annotation.Audit");
      }
    }
    return false;
  }

  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    return new FindUsagesHandler(element) {
      @NotNull
      @Override
      public PsiElement[] getSecondaryElements() {
        final PsiField psiField = (PsiField) getPsiElement();
        final PsiClass containingClass = psiField.getContainingClass();
        if (containingClass != null) {

          final Collection<PsiElement> elements = new ArrayList<>();
          processClass(containingClass, psiField, elements);

          Arrays.stream(containingClass.getInnerClasses())
            .forEach(psiClass -> processClass(psiClass, psiField, elements));

          return PsiUtilCore.toPsiElementArray(elements);
        }
        return PsiElement.EMPTY_ARRAY;
      }

      private void processClass(PsiClass containingClass, PsiField refPsiField, Collection<PsiElement> collector) {
        processClassMethods(containingClass, refPsiField, collector);
        processClassFields(containingClass, refPsiField, collector);
      }

      private void processClassFields(PsiClass containingClass, PsiField refPsiField, Collection<PsiElement> collector) {
        Arrays.stream(containingClass.getFields())
          .filter(LombokLightFieldBuilder.class::isInstance)
          .filter(psiField -> psiField.getNavigationElement() == refPsiField)
          .forEach(collector::add);
      }

      private void processClassMethods(PsiClass containingClass, PsiField refPsiField, Collection<PsiElement> collector) {
        Arrays.stream(containingClass.getMethods())
          .filter(LombokLightMethodBuilder.class::isInstance)
          .filter(psiMethod -> psiMethod.getNavigationElement() == refPsiField)
          .forEach(collector::add);
      }
    };
  }
}
