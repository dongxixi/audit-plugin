package com.fiture.plugin.audit.processor.clazz;

import com.fiture.plugin.audit.LombokClassNames;
import com.fiture.plugin.audit.problem.ProblemBuilder;
import com.fiture.plugin.audit.processor.field.GetterFieldProcessor;
import com.fiture.plugin.audit.processor.field.SetterFieldProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class AuditProcessor extends AbstractClassProcessor {
    protected AuditProcessor() {
        super(PsiMethod.class, LombokClassNames.AUDIT);
    }

    @Override
    protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
        if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
            builder.addError("'@Audit' is only supported on a class type");
            return false;
        }
        return true;
    }

    @Override
    protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
        Project project = psiClass.getProject();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiField creator = factory.createField("creator", PsiType.getTypeByName(String.class.getName(), project, GlobalSearchScope.allScope(project)));
        PsiField createTime = factory.createField("createTime", PsiType.getTypeByName(LocalDateTime.class.getName(), project, GlobalSearchScope.allScope(project)));
        SetterFieldProcessor setterFieldProcessor = ApplicationManager.getApplication().getService(SetterFieldProcessor.class);
        GetterFieldProcessor getterFieldProcessor = ApplicationManager.getApplication().getService(GetterFieldProcessor.class);
        target.add(setterFieldProcessor.createSetterMethod(creator, psiClass, PsiModifier.PUBLIC));
        target.add(setterFieldProcessor.createSetterMethod(createTime, psiClass, PsiModifier.PUBLIC));
        target.add(getterFieldProcessor.createGetterMethod(creator, psiClass, PsiModifier.PUBLIC));
        target.add(getterFieldProcessor.createGetterMethod(createTime, psiClass, PsiModifier.PUBLIC));
    }
}

