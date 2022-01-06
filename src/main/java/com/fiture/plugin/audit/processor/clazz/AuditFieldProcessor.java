package com.fiture.plugin.audit.processor.clazz;

import com.fiture.plugin.audit.LombokClassNames;
import com.fiture.plugin.audit.problem.ProblemBuilder;
import com.fiture.plugin.audit.psi.LombokLightFieldBuilder;
import com.fiture.plugin.audit.util.PsiClassUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AuditFieldProcessor extends AbstractClassProcessor {
    protected AuditFieldProcessor() {
        super(PsiField.class, LombokClassNames.AUDIT);
    }

    @Override
    protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
        if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
            builder.addError("'@Audit' is only supported on a class type");
            return false;
        }
        if (!psiClass.hasAnnotation(LombokClassNames.AUDIT)) {
            return false;
        }
        return true;
    }

    @Override
    protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
        Project project = psiClass.getProject();
        Set<String> allFieldNames = PsiClassUtil.collectClassFieldsIntern(psiClass).stream().map(PsiField::getName).collect(Collectors.toSet());
        if (!allFieldNames.contains("creator")) {
            PsiField creator = new LombokLightFieldBuilder(psiClass.getManager(), "creator", PsiType.getTypeByName(String.class.getName(), project, GlobalSearchScope.allScope(project)))
                    .setModifiers(PsiModifier.PRIVATE);
            target.add(creator);
        }
        if (!allFieldNames.contains("createTime")) {
            PsiField createTime = new LombokLightFieldBuilder(psiClass.getManager(), "createTime", PsiType.getTypeByName(LocalDateTime.class.getName(), project, GlobalSearchScope.allScope(project)))
                    .withModifier(PsiModifier.PRIVATE);
            target.add(createTime);
        }
    }
}

