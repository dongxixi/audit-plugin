package com.fiture.plugin.audit.convert;

import com.google.gson.JsonObject;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.psi.PsiElement;

public interface Convert {
    <T extends PsiElement> void upsert(JsonObject data, PsiTreeElementBase<T> template, String... bizKey);
}
