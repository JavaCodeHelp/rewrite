package org.ocpsoft.rewrite.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.ocpsoft.rewrite.bind.Binding;
import org.ocpsoft.rewrite.bind.Evaluation;
import org.ocpsoft.rewrite.config.Operation;
import org.ocpsoft.rewrite.context.EvaluationContext;
import org.ocpsoft.rewrite.event.Rewrite;
import org.ocpsoft.rewrite.exception.RewriteException;
import org.ocpsoft.rewrite.param.DefaultParameter;
import org.ocpsoft.rewrite.param.Parameter;
import org.ocpsoft.rewrite.param.ParameterStore;
import org.ocpsoft.rewrite.param.Parameterized;

/**
 * Utility methods for interactive with {@link ParameterStore} instances.
 * 
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
public class ParameterUtils
{
   /**
    * Initialize the {@link Parameterized} instance with the given {@link ParameterStore}, also record required
    * parameter names in the {@link ParameterStore} and initialize with a new {@link Parameter} instance.
    */
   public static void initialize(ParameterStore store, Parameterized parameterized)
   {
      Set<String> names = parameterized.getRequiredParameterNames();
      for (String name : names) {
         if (!store.contains(name))
            store.put(name, new DefaultParameter(name));
      }

      parameterized.setParameterStore(store);
   }

   /**
    * Submit the given value to all registered {@link Binding} instances of the given {@link Parameter}. Perform this by
    * adding individual {@link BindingOperation} instances via {@link EvaluationContext#addPreOperation(Operation)}
    */
   public static boolean enqueueSubmission(final Rewrite event, final EvaluationContext context,
            final Parameter<?> parameter, final Object value)
   {
      if (value == null)
         return true;

      List<Operation> operations = new ArrayList<Operation>();
      List<Binding> bindings = parameter.getBindings();
      for (Binding binding : bindings) {
         try {
            if (binding instanceof Evaluation)
            {
               /*
                * Binding to the EvaluationContext is available immediately.
                */
               Object convertedValue = ValueHolderUtil.convert(event, context, parameter.getConverter(), value);
               if (ValueHolderUtil.validates(event, context, parameter.getValidator(), convertedValue))
               {
                  Evaluation evaluation = (Evaluation) binding;
                  evaluation.submit(event, context, parameter, value);
                  evaluation.submitConverted(event, context, parameter, convertedValue);
               }
               else
                  return false;
            }
            else
            {
               Object convertedValue = ValueHolderUtil.convert(event, context, parameter.getConverter(), value);
               if (ValueHolderUtil.validates(event, context, parameter.getValidator(), convertedValue))
               {
                  operations.add(new BindingOperation(parameter, binding, convertedValue));
               }
               else
                  return false;
            }
         }
         catch (Exception e) {
            throw new RewriteException("Failed to bind value [" + value + "] to binding [" + binding + "]", e);
         }
      }

      for (Operation operation : operations) {
         context.addPreOperation(operation);
      }

      return true;
   }

   /**
    * Extract bound values from configured {@link Parameter} instances. Return a {@link List} of the extracted values.
    */
   public static Object performRetrieval(final Rewrite event, final EvaluationContext context,
            final Parameter<?> parameter)
   {
      Object result = null;

      List<Binding> bindings = new ArrayList<Binding>(parameter.getBindings());
      Collections.reverse(bindings);
      // FIXME Should not have to worry about order here.

      for (Binding binding : bindings)
      {
         if (result == null && !(binding instanceof Evaluation))
         {
            result = binding.retrieve(event, context, parameter);
         }
      }

      for (Binding binding : bindings)
      {
         if (binding instanceof Evaluation)
         {
            if (((Evaluation) binding).hasValue(event, context))
            {
               result = binding.retrieve(event, context, parameter);
            }
         }
      }

      return result;
   }

   /**
    * Used to store bindings until all conditions have been met.
    * 
    * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
    */
   private static class BindingOperation implements Operation
   {
      private final Parameter<?> parameter;
      private final Binding binding;
      private final Object value;

      public BindingOperation(final Parameter<?> parameter, final Binding binding, final Object value)
      {
         this.parameter = parameter;
         this.binding = binding;
         this.value = value;
      }

      @Override
      public void perform(final Rewrite event, final EvaluationContext context)
      {
         binding.submit(event, context, parameter, value);
      }

      @Override
      public String toString()
      {
         return "BindingOperation [binding=" + binding + ", value=" + value + "]";
      }
   }
}
