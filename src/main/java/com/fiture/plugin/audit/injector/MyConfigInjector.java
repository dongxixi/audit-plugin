package com.fiture.plugin.audit.injector;

import com.intellij.lang.injection.general.Injection;
import com.intellij.lang.injection.general.LanguageInjectionContributor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public final class MyConfigInjector implements LanguageInjectionContributor {

  public Injection getInjection(@NotNull PsiElement context) {
   // if (!isConfigPlace(context)) return null;
   //   if (shouldInjectYaml(context)) {
   //     return new SimpleInjection(
   //             YAMLLanguage.INSTANCE.getID(), "", "", null);
   //   }
   //   else if (shouldInjectJSON(context)) {
   //     return new SimpleInjection(
   //             JsonLanguage.INSTANCE, "", "", null);
   //   }
      return null;
    }
}
