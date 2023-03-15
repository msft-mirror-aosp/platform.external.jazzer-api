/*
 * Copyright 2023 Code Intelligence GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.code_intelligence.jazzer.mutation;

import static com.code_intelligence.jazzer.mutation.mutator.Mutators.validateAnnotationUsage;
import static com.code_intelligence.jazzer.mutation.support.ExceptionSupport.asUnchecked;
import static com.code_intelligence.jazzer.mutation.support.Preconditions.require;
import static com.code_intelligence.jazzer.mutation.support.StreamSupport.toArrayOrEmpty;
import static com.code_intelligence.jazzer.mutation.support.StreamSupport.toBooleanArray;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.code_intelligence.jazzer.mutation.annotation.SafeToMutate;
import com.code_intelligence.jazzer.mutation.api.MutatorFactory;
import com.code_intelligence.jazzer.mutation.api.PseudoRandom;
import com.code_intelligence.jazzer.mutation.api.SerializingMutator;
import com.code_intelligence.jazzer.mutation.combinator.MutatorCombinators;
import com.code_intelligence.jazzer.mutation.combinator.ProductMutator;
import com.code_intelligence.jazzer.mutation.engine.SeededPseudoRandom;
import com.code_intelligence.jazzer.mutation.mutator.Mutators;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

public final class ArgumentsMutator {
  private final Object instance;
  private final Method method;
  private final ProductMutator productMutator;
  private final boolean[] shouldDetach;
  private Object[] arguments;

  private ArgumentsMutator(
      Object instance, Method method, ProductMutator productMutator, boolean[] shouldDetach) {
    this.instance = instance;
    this.method = method;
    this.productMutator = productMutator;
    this.shouldDetach = shouldDetach;
  }

  private static String prettyPrintMethod(Method method) {
    return format("%s.%s(%s)", method.getDeclaringClass().getName(), method.getName(),
        stream(method.getAnnotatedParameterTypes()).map(Object::toString).collect(joining(", ")));
  }

  public static ArgumentsMutator forInstanceMethod(Object instance, Method method) {
    return forInstanceMethod(Mutators.newFactory(), instance, method)
        .orElseThrow(()
                         -> new IllegalArgumentException(
                             "Failed to construct mutator for " + prettyPrintMethod(method)));
  }

  public static ArgumentsMutator forStaticMethod(Method method) {
    return forStaticMethod(Mutators.newFactory(), method)
        .orElseThrow(()
                         -> new IllegalArgumentException(
                             "Failed to construct mutator for " + prettyPrintMethod(method)));
  }

  public static Optional<ArgumentsMutator> forInstanceMethod(
      MutatorFactory mutatorFactory, Object instance, Method method) {
    require(!isStatic(method), "method must not be static");
    requireNonNull(instance, "instance must not be null");
    require(method.getDeclaringClass().isInstance(instance),
        format("instance is a %s, expected %s", instance.getClass(), method.getDeclaringClass()));
    return forMethod(mutatorFactory, instance, method);
  }

  public static Optional<ArgumentsMutator> forStaticMethod(
      MutatorFactory mutatorFactory, Method method) {
    require(isStatic(method), "method must be static");
    return forMethod(mutatorFactory, null, method);
  }

  private static Optional<ArgumentsMutator> forMethod(
      MutatorFactory mutatorFactory, Object instance, Method method) {
    require(method.getParameterCount() > 0, "Can't fuzz method without parameters: " + method);
    for (AnnotatedType parameter : method.getAnnotatedParameterTypes()) {
      validateAnnotationUsage(parameter);
    }
    return toArrayOrEmpty(
        stream(method.getAnnotatedParameterTypes()).map(mutatorFactory::tryCreate),
        SerializingMutator<?>[] ::new)
        .map(MutatorCombinators::mutateProduct)
        .map(productMutator -> ArgumentsMutator.create(instance, method, productMutator));
  }

  private static ArgumentsMutator create(
      Object instance, Method method, ProductMutator productMutator) {
    method.setAccessible(true);

    boolean[] shouldDetach = toBooleanArray(
        stream(method.getParameterAnnotations()).map(ArgumentsMutator::isMutabilityRequested));

    return new ArgumentsMutator(instance, method, productMutator, shouldDetach);
  }

  private static boolean isMutabilityRequested(Annotation[] annotations) {
    return stream(annotations).anyMatch(annotation -> annotation instanceof SafeToMutate);
  }

  private static boolean isStatic(Method method) {
    return Modifier.isStatic(method.getModifiers());
  }

  /**
   * @throws UncheckedIOException if the underlying InputStream throws
   */
  public void read(InputStream data) {
    try {
      this.arguments = productMutator.readExclusive(data);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * @throws UncheckedIOException if the underlying OutputStream throws
   */
  public void write(OutputStream data) {
    try {
      productMutator.writeExclusive(arguments, data);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void init(long seed) {
    init(new SeededPseudoRandom(seed));
  }

  void init(PseudoRandom prng) {
    this.arguments = productMutator.init(prng);
  }

  public void mutate(long seed) {
    mutate(new SeededPseudoRandom(seed));
  }

  void mutate(PseudoRandom prng) {
    // TODO: Sometimes mutate the entire byte representation of the current value with libFuzzer's
    //  dictionary and TORC mutations.
    productMutator.mutate(arguments, prng);
  }

  public void invoke() throws Throwable {
    // TODO: Sometimes hash the serialized value before and after the invocation and check that the
    //  hashes match to catch fuzz tests that mutate mutable inputs (e.g. byte[]).
    //  Alternatively, always detach arguments and instead of the SafeToMutate annotation have a
    //  Mutable annotation that can be used to e.g. receive a mutable implementation of List. This
    //  is always safe, but could incur additional overhead for arrays.
    try {
      method.invoke(instance, productMutator.detachSelectively(arguments, shouldDetach));
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("method should have been made accessible", e);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  @Override
  public String toString() {
    return "Arguments" + productMutator;
  }
}