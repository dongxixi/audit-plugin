package com.fiture.plugin.audit.convert;

import com.google.gson.JsonObject;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public interface DataCollector {
    <T extends PsiElement> JsonObject getData(PsiFile source);
}
