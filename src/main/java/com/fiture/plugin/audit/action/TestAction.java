package com.fiture.plugin.audit.action;

import com.fiture.plugin.audit.ui.PsiViewerDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;

public class TestAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        // PsiClass aClass = JavaPsiFacade.getInstance(project).findClass("StockBillTypeEnum", GlobalSearchScope.projectScope(project));
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        // SelectionModel selectionModel = editor.getSelectionModel();
        // selectionModel.selectWordAtCaret(false);
        // PsiTreeElementBase<PsiClass> psiTreeElementBase = new PsiTreeElementBase<>(aClass) {
        //     @Override
        //     public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
        //         return Arrays.stream(aClass.getFields()).map(c -> new PsiFieldTreeElement(c, false)).collect(Collectors.toList());
        //     }
        //
        //     @Override
        //     public @NlsSafe @Nullable String getPresentableText() {
        //         return "null";
        //     }
        // };
        // psiTreeElementBase.getChildren();
        new PsiViewerDialog(project, editor).show();
    }
}
