package com.fiture.plugin.audit.ui;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.internal.psiView.ViewerTreeBuilder;
import com.intellij.internal.psiView.ViewerTreeStructure;
import com.intellij.internal.psiView.formattingblocks.BlockTreeNode;
import com.intellij.internal.psiView.formattingblocks.BlockTreeStructure;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.JBColor;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Objects;
import java.util.concurrent.Callable;

public class Test extends DialogWrapper implements DataProvider {
    private JPanel panel1;
    private JPanel textPanel;
    private Tree myTree;


    private Project project;

    private EditorEx myEditor;
    private EditorListener editorListener = new EditorListener();
    private ViewerTreeBuilder viewerTreeBuilder;
    private PsiFile myOriginalPsiFile;

    public Test(@Nullable Project project, Editor selectedEditor) {
        super(project, false, IdeModalityType.MODELESS);
        myEditor = (EditorEx) EditorFactory.getInstance().createEditor(selectedEditor.getDocument(), project);
        textPanel.setLayout(new BorderLayout());
        textPanel.add(myEditor.getComponent(), BorderLayout.CENTER);
        setTitle("Test");
        init();
        this.project = project;
        myEditor.getCaretModel().addCaretListener(editorListener);
        myOriginalPsiFile = getOriginalPsiFile(project, selectedEditor);
        AsyncTreeModel asyncTreeModel = new AsyncTreeModel(myTree.getModel(), getDisposable());
    }

    private void createUIComponents() {
        myTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
        viewerTreeBuilder = new ViewerTreeBuilder(project, myTree);
        Disposer.register(getDisposable(), viewerTreeBuilder);
    }
    private static @Nullable PsiFile getOriginalPsiFile(@NotNull Project project, @Nullable Editor selectedEditor) {
        return selectedEditor != null ? PsiDocumentManager.getInstance(project).getPsiFile(selectedEditor.getDocument()) : null;
    }
    @Override
    public @Nullable Object getData(@NotNull @NonNls String dataId) {
        return null;
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return panel1;
    }

    @NotNull
    private ViewerTreeStructure getTreeStructure() {
        return Objects.requireNonNull((ViewerTreeStructure) viewerTreeBuilder.getTreeStructure());
    }

    @Override
    protected void doOKAction() {
        PsiElement rootElement = parseText(myEditor.getDocument().getText());
        Block block = buildBlocks(rootElement);
        BlockTreeNode blockNode = new BlockTreeNode(block, null);
        BlockTreeStructure blockTreeStructure = new BlockTreeStructure();
        blockTreeStructure.setRoot(blockNode);
        Disposable parent = Disposer.newDisposable();
        StructureTreeModel<BlockTreeStructure> structureTreeModel = new StructureTreeModel<>(blockTreeStructure, parent);
        AsyncTreeModel asyncTreeModel = new AsyncTreeModel(structureTreeModel, parent);
        myTree.setModel(asyncTreeModel);
        LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(PsiManager.getInstance(project).findFile(myEditor.getVirtualFile()));
    }

    private class EditorListener implements SelectionListener, DocumentListener, CaretListener {

        @Override
        public void caretPositionChanged(@NotNull CaretEvent e) {
            if (!available() || myEditor.getSelectionModel().hasSelection()) return;
            final ViewerTreeStructure treeStructure = getTreeStructure();
            final PsiElement rootPsiElement = treeStructure.getRootPsiElement();
            if (rootPsiElement == null) return;
            final PsiElement rootElement = (getTreeStructure()).getRootPsiElement();
            int baseOffset = rootPsiElement.getTextRange().getStartOffset();
            final int offset = myEditor.getCaretModel().getOffset() + baseOffset;
            final PsiElement element = InjectedLanguageManager.getInstance(project).findInjectedElementAt(rootElement.getContainingFile(), offset);
            viewerTreeBuilder.select(element);
            // final PsiElement element = computeSlowOperationsSafeInBgThread(project, "progressDialogTitle", finder);
            TextRange textRange = element.getTextRange();

            TextAttributes highlightReferenceTextRange = new TextAttributes(null, null,
                    JBColor.namedColor("PsiViewer.referenceHighlightColor", 0xA8C023),
                    EffectType.BOLD_DOTTED_LINE, Font.PLAIN);
            myEditor.getMarkupModel().addRangeHighlighter(textRange.getStartOffset(), textRange.getEndOffset(), HighlighterLayer.LAST,
                    highlightReferenceTextRange, HighlighterTargetArea.EXACT_RANGE);
        }

        @Override
        public void selectionChanged(@NotNull SelectionEvent e) {
            if (!available() || !myEditor.getSelectionModel().hasSelection()) return;
            final PsiElement rootElement = parseText(myEditor.getDocument().getText());
            if (rootElement == null) return;
            final SelectionModel selection = myEditor.getSelectionModel();
            final TextRange textRange = rootElement.getTextRange();
            int baseOffset = textRange != null ? textRange.getStartOffset() : 0;
            final int start = selection.getSelectionStart() + baseOffset;
            final int end = selection.getSelectionEnd() + baseOffset - 1;

            final Callable<PsiElement> finder =
                    () -> findCommonParent(InjectedLanguageManager.getInstance(project).findInjectedElementAt(rootElement.getContainingFile(), start),
                            InjectedLanguageManager.getInstance(project).findInjectedElementAt(rootElement.getContainingFile(), end));

            final PsiElement element = computeSlowOperationsSafeInBgThread(project, "progressDialogTitle1", finder);
        }

        @Nullable
        private PsiElement findCommonParent(PsiElement start, PsiElement end) {
            if (end == null || start == end) {
                return start;
            }
            final TextRange endRange = end.getTextRange();
            PsiElement parent = start.getContext();
            while (parent != null && !parent.getTextRange().contains(endRange)) {
                parent = parent.getContext();
            }
            return parent;
        }

        private boolean available() {
            return true;
        }

        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
            // myNewDocumentHashCode = event.getDocument().getText().hashCode();
        }
    }

    private PsiElement parseText(String text) {
        return PsiFileFactory.getInstance(project).createFileFromText("Dummy.java", Language.findLanguageByID("JAVA"), text);
    }

    private static <T> T computeSlowOperationsSafeInBgThread(@NotNull Project project,
                                                             @NlsContexts.DialogTitle @NotNull String progressDialogTitle,
                                                             @NotNull Callable<T> callable) {

        return ProgressManager.getInstance().run(new Task.WithResult<>(project, progressDialogTitle, true) {
            @Override
            protected T compute(@NotNull ProgressIndicator indicator) throws RuntimeException {
                return ReadAction.nonBlocking(callable).executeSynchronously();
            }
        });
    }
    private static Block buildBlocks(@NotNull PsiElement rootElement) {
        FormattingModelBuilder formattingModelBuilder = LanguageFormatting.INSTANCE.forContext(rootElement);
        CodeStyleSettings settings = CodeStyle.getSettings(rootElement.getContainingFile());
        if (formattingModelBuilder != null) {
            FormattingModel formattingModel = formattingModelBuilder.createModel(
                    FormattingContext.create(rootElement, settings));
            return formattingModel.getRootBlock();
        }
        else {
            return null;
        }
    }
}
