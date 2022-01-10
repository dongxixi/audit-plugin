// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.fiture.plugin.audit.ui;

import com.intellij.codeInsight.documentation.render.DocRenderManager;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.internal.psiView.PsiViewerSettings;
import com.intellij.internal.psiView.ViewerNodeDescriptor;
import com.intellij.internal.psiView.ViewerTreeBuilder;
import com.intellij.internal.psiView.ViewerTreeStructure;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.indexing.DumbModeAccessType;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

/**
 * @author Konstantin Bulenkov
 */
public class PsiViewerDialog extends DialogWrapper implements DataProvider {
    public static final Color BOX_COLOR = new JBColor(new Color(0xFC6C00), new Color(0xDE6C01));
    public static final Logger LOG = Logger.getInstance(PsiViewerDialog.class);
    private final Project myProject;


    private JPanel myPanel;
    private JPanel myTextPanel;
    private JSplitPane myTextSplit;
    private JSplitPane myTreeSplit;
    private Tree myPsiTree;
    private ViewerTreeBuilder myPsiTreeBuilder;
    private final JList<String> myRefs;

    private TitledSeparator myTextSeparator;
    private TitledSeparator myPsiTreeSeparator;

    private RangeHighlighter myHighlighter;


    private final EditorEx myEditor;
    private final EditorListener myEditorListener = new EditorListener();
    private String myLastParsedText = null;
    private int myLastParsedTextHashCode = 17;
    private int myNewDocumentHashCode = 11;

    private final boolean myExternalDocument;

    private final Map<PsiElement, PsiElement[]> myRefsResolvedCache = new HashMap<>();

    private final PsiFile myOriginalPsiFile;

    private void createUIComponents() {
        myPsiTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    }


    public PsiViewerDialog(@NotNull Project project, @Nullable Editor selectedEditor) {
        super(project, true, IdeModalityType.MODELESS);
        myProject = project;
        myExternalDocument = selectedEditor != null;
        myOriginalPsiFile = getOriginalPsiFile(project, selectedEditor);
        myRefs = new JBList<>(new DefaultListModel<>());

        setOKButtonText("&Build PSI Tree");
        setCancelButtonText("&Close");
        Disposer.register(myProject, getDisposable());
        VirtualFile selectedFile = selectedEditor == null ? null : FileDocumentManager.getInstance().getFile(selectedEditor.getDocument());
        setTitle(selectedFile == null ? "PSI Viewer" : "PSI Viewer: " + selectedFile.getName());
        if (selectedEditor != null) {
            myEditor = (EditorEx) EditorFactory.getInstance().createEditor(selectedEditor.getDocument(), myProject);
        } else {
            PsiViewerSettings settings = PsiViewerSettings.getSettings();
            Document document = EditorFactory.getInstance().createDocument(StringUtil.notNullize(settings.text));
            myEditor = (EditorEx) EditorFactory.getInstance().createEditor(document, myProject);
            myEditor.getSelectionModel().setSelection(0, document.getTextLength());
        }
        myEditor.getSettings().setLineMarkerAreaShown(false);
        DocRenderManager.setDocRenderingEnabled(myEditor, false);
        init();
        if (selectedEditor != null) {
            doOKAction();

            ApplicationManager.getApplication().invokeLater(() -> {
                getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(myEditor.getContentComponent(), true));
                myEditor.getCaretModel().moveToOffset(selectedEditor.getCaretModel().getOffset());
                myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
            }, ModalityState.stateForComponent(myPanel));
        }
    }

    private static @Nullable PsiFile getOriginalPsiFile(@NotNull Project project, @Nullable Editor selectedEditor) {
        return selectedEditor != null ? PsiDocumentManager.getInstance(project).getPsiFile(selectedEditor.getDocument()) : null;
    }

    @Override
    protected void init() {
        initMnemonics();

        initTree(myPsiTree);
        final TreeCellRenderer renderer = myPsiTree.getCellRenderer();
        myPsiTree.setCellRenderer(new TreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(@NotNull JTree tree,
                                                          Object value,
                                                          boolean selected,
                                                          boolean expanded,
                                                          boolean leaf,
                                                          int row,
                                                          boolean hasFocus) {
                final Component c = renderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                if (value instanceof DefaultMutableTreeNode) {
                    final Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
                    if (userObject instanceof ViewerNodeDescriptor) {
                        final Object element = ((ViewerNodeDescriptor) userObject).getElement();
                        if (c instanceof NodeRenderer) {
                            ((NodeRenderer) c).setToolTipText(element == null ? null : element.getClass().getName());
                        }
                        boolean flag;
                        try {
                            flag = Class.forName("com.intellij.internal.psiView.ViewerTreeStructure.Inject").isInstance(element);
                        } catch (ClassNotFoundException e) {
                            flag = false;
                        }
                        if (element instanceof PsiElement && FileContextUtil.getFileContext(((PsiElement) element).getContainingFile()) != null ||
                                flag) {
                            final TextAttributes attr =
                                    EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.INJECTED_LANGUAGE_FRAGMENT);
                            c.setBackground(attr.getBackgroundColor());
                        }
                    }
                }
                return c;
            }
        });
        myPsiTreeBuilder = new ViewerTreeBuilder(myProject, myPsiTree);
        Disposer.register(getDisposable(), myPsiTreeBuilder);
        myPsiTree.addTreeSelectionListener(new MyPsiTreeSelectionListener());

        JPanel panelWrapper = new JPanel(new BorderLayout());
        myTreeSplit.add(panelWrapper, JSplitPane.RIGHT);

        JPanel referencesPanel = new JPanel(new BorderLayout());
        referencesPanel.add(myRefs);
        referencesPanel.setBorder(IdeBorderFactory.createBorder());

        PsiViewerSettings settings = PsiViewerSettings.getSettings();

        final GoToListener listener = new GoToListener();
        myRefs.addKeyListener(listener);
        myRefs.addMouseListener(listener);
        myRefs.getSelectionModel().addListSelectionListener(listener);
        myRefs.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(@NotNull JList list,
                                                          Object value,
                                                          int index,
                                                          boolean isSelected,
                                                          boolean cellHasFocus) {
                final Component comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                final PsiElement[] elements = myRefsResolvedCache.get(getPsiElement());
                if (elements == null || elements.length <= index || elements[index] == null) {
                    comp.setForeground(JBColor.RED);
                }
                return comp;
            }
        });

        myEditor.getSettings().setFoldingOutlineShown(false);
        myEditor.getDocument().addDocumentListener(myEditorListener, getDisposable());
        myEditor.getSelectionModel().addSelectionListener(myEditorListener);
        myEditor.getCaretModel().addCaretListener(myEditorListener);

        FocusTraversalPolicy oldPolicy = getPeer().getWindow().getFocusTraversalPolicy();
        getPeer().getWindow().setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
            @Override
            public Component getInitialComponent(@NotNull Window window) {
                return myEditor.getComponent();
            }
        });
        Disposer.register(getDisposable(), () -> {
            getPeer().getWindow().setFocusTraversalPolicy(oldPolicy);
        });
        VirtualFile file = myExternalDocument ? FileDocumentManager.getInstance().getFile(myEditor.getDocument()) : null;
        Language curLanguage = LanguageUtil.getLanguageForPsi(myProject, file);

        String type = curLanguage != null ? curLanguage.getDisplayName() : settings.type;


        final ViewerTreeStructure psiTreeStructure = getTreeStructure();
        psiTreeStructure.setShowWhiteSpaces(settings.showWhiteSpaces);
        psiTreeStructure.setShowTreeNodes(settings.showTreeNodes);
        myTextPanel.setLayout(new BorderLayout());
        myTextPanel.add(myEditor.getComponent(), BorderLayout.CENTER);

        registerCustomKeyboardActions();

        final Dimension size = DimensionService.getInstance().getSize(getDimensionServiceKey(), myProject);
        if (size == null) {
            DimensionService.getInstance().setSize(getDimensionServiceKey(), JBUI.size(800, 600), myProject);
        }
        myTextSplit.setDividerLocation(settings.textDividerLocation);
        myTreeSplit.setDividerLocation(settings.treeDividerLocation);

        updateEditor();
        super.init();
    }

    public static void initTree(JTree tree) {
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.updateUI();
        ToolTipManager.sharedInstance().registerComponent(tree);
        TreeUtil.installActions(tree);
        new TreeSpeedSearch(tree);
    }

    @Override
    @NotNull
    protected String getDimensionServiceKey() {
        return "#com.intellij.internal.psiView.PsiViewerDialog";
    }

    @Override
    protected String getHelpId() {
        return "reference.psi.viewer";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return myEditor.getContentComponent();
    }

    private void registerCustomKeyboardActions() {
        final int mask = SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.ALT_DOWN_MASK;

        registerKeyboardAction(e -> focusEditor(), KeyStroke.getKeyStroke(KeyEvent.VK_T, mask));

        registerKeyboardAction(e -> focusTree(), KeyStroke.getKeyStroke(KeyEvent.VK_S, mask));

    }

    private void registerKeyboardAction(ActionListener actionListener, KeyStroke keyStroke) {
        getRootPane().registerKeyboardAction(actionListener, keyStroke, JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void focusEditor() {
        IdeFocusManager.getInstance(myProject).requestFocus(myEditor.getContentComponent(), true);
    }

    private void focusTree() {
        IdeFocusManager.getInstance(myProject).requestFocus(myPsiTree, true);
    }

    private void initMnemonics() {
        myTextSeparator.setLabelFor(myEditor.getContentComponent());
        myPsiTreeSeparator.setLabelFor(myPsiTree);
    }

    @Nullable
    private PsiElement getPsiElement() {
        final TreePath path = myPsiTree.getSelectionPath();
        return path == null ? null : getPsiElement((DefaultMutableTreeNode) path.getLastPathComponent());
    }

    @Nullable
    private static PsiElement getPsiElement(DefaultMutableTreeNode node) {
        if (node.getUserObject() instanceof ViewerNodeDescriptor) {
            ViewerNodeDescriptor descriptor = (ViewerNodeDescriptor) node.getUserObject();
            Object elementObject = descriptor.getElement();
            return elementObject instanceof PsiElement
                    ? (PsiElement) elementObject
                    : elementObject instanceof ASTNode ? ((ASTNode) elementObject).getPsi() : null;
        }
        return null;
    }

    @Override
    protected JComponent createCenterPanel() {
        return myPanel;
    }


    @Override
    protected void doOKAction() {

        final String text = myEditor.getDocument().getText();
        myEditor.getSelectionModel().removeSelection();

        myLastParsedText = text;
        myLastParsedTextHashCode = text.hashCode();
        myNewDocumentHashCode = myLastParsedTextHashCode;
        PsiElement rootElement = parseText(text);
        ObjectUtils.consumeIfNotNull(rootElement, e -> e.getContainingFile().putUserData(PsiFileFactory.ORIGINAL_FILE, myOriginalPsiFile));
        focusTree();
        ViewerTreeStructure structure = getTreeStructure();
        structure.setRootPsiElement(rootElement);

        myPsiTreeBuilder.queueUpdate();
        myPsiTree.setRootVisible(true);
        myPsiTree.expandRow(0);
        myPsiTree.setRootVisible(false);

        myRefsResolvedCache.clear();
    }


    @NotNull
    private ViewerTreeStructure getTreeStructure() {
        return Objects.requireNonNull((ViewerTreeStructure) myPsiTreeBuilder.getTreeStructure());
    }

    private PsiElement parseText(String text) {
        try {
            return PsiFileFactory.getInstance(myProject).createFileFromText("Dummy.java", Language.findLanguageByID("JAVA"), text);
        } catch (IncorrectOperationException e) {
            Messages.showMessageDialog(myProject, e.getMessage(), "Error", Messages.getErrorIcon());
        }
        return null;
    }


    @Override
    public Object getData(@NotNull @NonNls String dataId) {
        if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
            String fqn = null;
            if (myPsiTree.hasFocus()) {
                final TreePath path = myPsiTree.getSelectionPath();
                if (path != null) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                    if (!(node.getUserObject() instanceof ViewerNodeDescriptor)) return null;
                    ViewerNodeDescriptor descriptor = (ViewerNodeDescriptor) node.getUserObject();
                    Object elementObject = descriptor.getElement();
                    final PsiElement element = elementObject instanceof PsiElement
                            ? (PsiElement) elementObject
                            : elementObject instanceof ASTNode ? ((ASTNode) elementObject).getPsi() : null;
                    if (element != null) {
                        fqn = element.getClass().getName();
                    }
                }
            } else if (myRefs.hasFocus()) {
                fqn = myRefs.getSelectedValue();
            }
            if (fqn != null) {
                return getContainingFileForClass(fqn);
            }
        }
        return null;
    }

    private class MyPsiTreeSelectionListener implements TreeSelectionListener {
        private final TextAttributes myAttributes;

        MyPsiTreeSelectionListener() {
            myAttributes = new TextAttributes();
            myAttributes.setEffectColor(BOX_COLOR);
            myAttributes.setEffectType(EffectType.ROUNDED_BOX);
        }

        @Override
        public void valueChanged(@NotNull TreeSelectionEvent e) {
            if (!myEditor.getDocument().getText().equals(myLastParsedText)) return;
            TreePath path = myPsiTree.getSelectionPath();
            clearSelection();
            if (path != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                if (!(node.getUserObject() instanceof ViewerNodeDescriptor)) return;
                ViewerNodeDescriptor descriptor = (ViewerNodeDescriptor) node.getUserObject();
                Object elementObject = descriptor.getElement();
                final PsiElement element = elementObject instanceof PsiElement
                        ? (PsiElement) elementObject
                        : elementObject instanceof ASTNode ? ((ASTNode) elementObject).getPsi() : null;
                if (element != null) {
                    TextRange rangeInHostFile = InjectedLanguageManager.getInstance(myProject).injectedToHost(element, element.getTextRange());
                    int start = rangeInHostFile.getStartOffset();
                    int end = rangeInHostFile.getEndOffset();
                    final ViewerTreeStructure treeStructure = getTreeStructure();
                    PsiElement rootPsiElement = treeStructure.getRootPsiElement();
                    if (rootPsiElement != null) {
                        int baseOffset = rootPsiElement.getTextRange().getStartOffset();
                        start -= baseOffset;
                        end -= baseOffset;
                    }
                    final int textLength = myEditor.getDocument().getTextLength();
                    if (end <= textLength) {
                        myHighlighter = myEditor.getMarkupModel()
                                .addRangeHighlighter(start, end, HighlighterLayer.LAST, myAttributes, HighlighterTargetArea.EXACT_RANGE);
                        if (myPsiTree.hasFocus()) {
                            myEditor.getCaretModel().moveToOffset(start);
                            myEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                        }
                    }
                    updateReferences(element);
                }
            }
        }
    }

    public void updateReferences(PsiElement element) {
        final DefaultListModel<String> model = (DefaultListModel<String>) myRefs.getModel();
        model.clear();

        if (element == null) return;

        final String progressTitle = "psi.viewer.progress.dialog.update.refs";
        final Callable<List<PsiReference>> updater =
                () -> DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> doUpdateReferences(element));

        final List<PsiReference> psiReferences = computeSlowOperationsSafeInBgThread(myProject, progressTitle, updater);

        for (PsiReference reference : psiReferences) {
            model.addElement(reference.getClass().getName());
        }
    }

    private @NotNull List<PsiReference> doUpdateReferences(@NotNull PsiElement element) {
        final PsiReferenceService referenceService = PsiReferenceService.getService();
        final List<PsiReference> psiReferences = referenceService.getReferences(element, PsiReferenceService.Hints.NO_HINTS);

        if (myRefsResolvedCache.containsKey(element)) return psiReferences;

        final PsiElement[] cache = new PsiElement[psiReferences.size()];

        for (int i = 0; i < psiReferences.size(); i++) {
            final PsiReference reference = psiReferences.get(i);

            final PsiElement resolveResult;
            if (reference instanceof PsiPolyVariantReference) {
                final ResolveResult[] results = ((PsiPolyVariantReference) reference).multiResolve(true);
                resolveResult = results.length == 0 ? null : results[0].getElement();
            } else {
                resolveResult = reference.resolve();
            }
            cache[i] = resolveResult;
        }
        myRefsResolvedCache.put(element, cache);

        return psiReferences;
    }

    private void clearSelection() {
        if (myHighlighter != null) {
            myEditor.getMarkupModel().removeHighlighter(myHighlighter);
            myHighlighter.dispose();
        }
    }

    @Override
    public void doCancelAction() {
        super.doCancelAction();
        PsiViewerSettings settings = PsiViewerSettings.getSettings();
        settings.textDividerLocation = myTextSplit.getDividerLocation();
        settings.treeDividerLocation = myTreeSplit.getDividerLocation();
    }

    @Override
    public void dispose() {
        if (!myEditor.isDisposed()) {
            EditorFactory.getInstance().releaseEditor(myEditor);
        }
        super.dispose();
    }

    @Nullable
    private PsiFile getContainingFileForClass(String fqn) {
        String filename = fqn;
        if (fqn.contains(".")) {
            filename = fqn.substring(fqn.lastIndexOf('.') + 1);
        }
        if (filename.contains("$")) {
            filename = filename.substring(0, filename.indexOf('$'));
        }
        filename += ".java";
        final PsiFile[] files = FilenameIndex.getFilesByName(myProject, filename, GlobalSearchScope.allScope(myProject));
        return ArrayUtil.getFirstElement(files);
    }

    private class GoToListener implements KeyListener, MouseListener, ListSelectionListener {
        private RangeHighlighter myListenerHighlighter;

        private void navigate() {
            final String fqn = myRefs.getSelectedValue();
            final PsiFile file = getContainingFileForClass(fqn);
            if (file != null) file.navigate(true);
        }

        @Override
        public void keyPressed(@NotNull KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                navigate();
            }
        }

        @Override
        public void mouseClicked(@NotNull MouseEvent e) {
            if (e.getClickCount() > 1) {
                navigate();
            }
        }

        @Override
        public void valueChanged(@NotNull ListSelectionEvent e) {
            clearSelection();
            final int ind = myRefs.getSelectedIndex();
            final PsiElement element = getPsiElement();
            if (ind > -1 && element != null) {
                final PsiReference[] references = element.getReferences();
                if (ind < references.length) {
                    final TextRange textRange = references[ind].getRangeInElement();
                    TextRange range = InjectedLanguageManager.getInstance(myProject).injectedToHost(element, element.getTextRange());
                    int start = range.getStartOffset();
                    int end = range.getEndOffset();
                    final ViewerTreeStructure treeStructure = getTreeStructure();
                    PsiElement rootPsiElement = treeStructure.getRootPsiElement();
                    if (rootPsiElement != null) {
                        int baseOffset = rootPsiElement.getTextRange().getStartOffset();
                        start -= baseOffset;
                        end -= baseOffset;
                    }

                    start += textRange.getStartOffset();
                    end = start + textRange.getLength();
                    //todo[kb] probably move highlight color to the editor color scheme?
                    TextAttributes highlightReferenceTextRange = new TextAttributes(null, null,
                            JBColor.namedColor("PsiViewer.referenceHighlightColor", 0xA8C023),
                            EffectType.BOLD_DOTTED_LINE, Font.PLAIN);
                    myListenerHighlighter = myEditor.getMarkupModel()
                            .addRangeHighlighter(start, end, HighlighterLayer.LAST,
                                    highlightReferenceTextRange, HighlighterTargetArea.EXACT_RANGE);
                }
            }
        }

        public void clearSelection() {
            if (myListenerHighlighter != null &&
                    ArrayUtil.contains(myListenerHighlighter, (Object[]) myEditor.getMarkupModel().getAllHighlighters())) {
                myListenerHighlighter.dispose();
                myListenerHighlighter = null;
            }
        }

        @Override
        public void keyTyped(@NotNull KeyEvent e) {
        }

        @Override
        public void keyReleased(KeyEvent e) {
        }

        @Override
        public void mousePressed(@NotNull MouseEvent e) {
        }

        @Override
        public void mouseReleased(@NotNull MouseEvent e) {
        }

        @Override
        public void mouseEntered(@NotNull MouseEvent e) {
        }

        @Override
        public void mouseExited(@NotNull MouseEvent e) {
        }
    }

    private void updateEditor() {

        final String fileName = "Dummy.java";
        final LightVirtualFile lightFile;
        lightFile = new LightVirtualFile(fileName, Language.findLanguageByID("JAVA"), "");
        EditorHighlighter highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, lightFile);
        try {
            myEditor.setHighlighter(highlighter);
        } catch (Throwable e) {
            LOG.warn(e);
        }
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
            final PsiElement element = InjectedLanguageUtil.findElementAtNoCommit(rootElement.getContainingFile(), offset);

            myPsiTreeBuilder.select(element);
        }

        @Override
        public void selectionChanged(@NotNull SelectionEvent e) {
            if (!available() || !myEditor.getSelectionModel().hasSelection()) return;
            ViewerTreeStructure treeStructure = getTreeStructure();
            final PsiElement rootElement = treeStructure.getRootPsiElement();
            if (rootElement == null) return;
            final SelectionModel selection = myEditor.getSelectionModel();
            final TextRange textRange = rootElement.getTextRange();
            int baseOffset = textRange != null ? textRange.getStartOffset() : 0;
            final int start = selection.getSelectionStart() + baseOffset;
            final int end = selection.getSelectionEnd() + baseOffset - 1;

            final String progressDialogTitle = "psi.viewer.progress.dialog.get.common.parent";
            final Callable<PsiElement> finder =
                    () -> findCommonParent(InjectedLanguageManager.getInstance(myProject).findInjectedElementAt(rootElement.getContainingFile(), start),
                            InjectedLanguageManager.getInstance(myProject).findInjectedElementAt(rootElement.getContainingFile(), end));

            final PsiElement element = computeSlowOperationsSafeInBgThread(myProject, progressDialogTitle, finder);
            myPsiTreeBuilder.select(element);
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
            return myLastParsedTextHashCode == myNewDocumentHashCode && myEditor.getContentComponent().hasFocus();
        }

        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
            myNewDocumentHashCode = event.getDocument().getText().hashCode();
        }
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
}
