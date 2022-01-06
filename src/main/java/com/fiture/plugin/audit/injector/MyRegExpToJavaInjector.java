// package com.fiture.plugin.audit.injector;
//
// import com.intellij.lang.injection.MultiHostInjector;
// import com.intellij.lang.injection.MultiHostRegistrar;
// import com.intellij.psi.PsiElement;
// import com.intellij.psi.PsiLiteralExpression;
// import org.intellij.lang.regexp.RegExpLanguage;
// import org.jetbrains.annotations.NotNull;
//
// import java.util.List;
//
// public class MyRegExpToJavaInjector implements MultiHostInjector {
//     @Override
//     public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
//         if (context instanceof PsiLiteralExpression) {
//             registrar.startInjecting(RegExpLanguage.INSTANCE)
//                     .addPlace(null,null,context,)
//         }
//     }
//
//     @Override
//     public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
//         return null;
//     }
// }
