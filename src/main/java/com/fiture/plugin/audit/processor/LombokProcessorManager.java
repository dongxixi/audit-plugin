package com.fiture.plugin.audit.processor;

import com.fiture.plugin.audit.processor.clazz.AuditFieldProcessor;
import com.fiture.plugin.audit.processor.clazz.AuditProcessor;
import com.fiture.plugin.audit.processor.clazz.GetterProcessor;
import com.fiture.plugin.audit.processor.clazz.SetterProcessor;
import com.fiture.plugin.audit.processor.field.GetterFieldProcessor;
import com.fiture.plugin.audit.processor.field.SetterFieldProcessor;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public final class LombokProcessorManager {
  @NotNull
  public static Collection<Processor> getLombokProcessors() {
    return Arrays.asList(

      ApplicationManager.getApplication().getService(GetterProcessor.class),
      ApplicationManager.getApplication().getService(SetterProcessor.class),
      ApplicationManager.getApplication().getService(AuditProcessor.class),
      ApplicationManager.getApplication().getService(AuditFieldProcessor.class),


      ApplicationManager.getApplication().getService(GetterFieldProcessor.class),
      ApplicationManager.getApplication().getService(SetterFieldProcessor.class),

      ApplicationManager.getApplication().getService(CleanupProcessor.class)
    );
  }
}
