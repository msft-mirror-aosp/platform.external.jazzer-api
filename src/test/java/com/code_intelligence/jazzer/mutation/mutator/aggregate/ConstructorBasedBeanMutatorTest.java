/*
 * Copyright 2024 Code Intelligence GmbH
 *
 * By downloading, you agree to the Code Intelligence Jazzer Terms and Conditions.
 *
 * The Code Intelligence Jazzer Terms and Conditions are provided in LICENSE-JAZZER.txt
 * located in the root directory of the project.
 */

package com.code_intelligence.jazzer.mutation.mutator.aggregate;

import static com.code_intelligence.jazzer.mutation.support.TestSupport.anyPseudoRandom;
import static com.google.common.truth.Truth.assertThat;

import com.code_intelligence.jazzer.mutation.annotation.NotNull;
import com.code_intelligence.jazzer.mutation.api.PseudoRandom;
import com.code_intelligence.jazzer.mutation.api.SerializingMutator;
import com.code_intelligence.jazzer.mutation.mutator.Mutators;
import com.code_intelligence.jazzer.mutation.support.TypeHolder;
import java.beans.ConstructorProperties;
import java.util.Objects;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"unchecked", "unused"})
class ConstructorBasedBeanMutatorTest {

  // This class is used to test constructors annotated with @ConstructorProperties,
  // which has precedence over property name and property type based getter detection.
  static class ConstructorPropertiesAnnotatedBean {
    private final boolean foo;
    private final String bar;
    private final int baz;

    @ConstructorProperties({"foo", "BAR", "baz"})
    ConstructorPropertiesAnnotatedBean(boolean a, String b, int c) {
      this.foo = a;
      this.bar = b;
      this.baz = c;
    }

    public boolean isFoo() {
      return foo;
    }

    public String getBAR() {
      return bar;
    }

    int getBaz() {
      return baz;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ConstructorPropertiesAnnotatedBean that = (ConstructorPropertiesAnnotatedBean) o;
      return foo == that.foo && baz == that.baz && Objects.equals(bar, that.bar);
    }

    @Override
    public int hashCode() {
      return Objects.hash(foo, bar, baz);
    }

    @Override
    public String toString() {
      return "SimpleTypeBean{" + "foo=" + foo + ", bar='" + bar + '\'' + ", baz=" + baz + '}';
    }
  }

  @Test
  void testConstructorPropertiesAnnotatedBean() {
    SerializingMutator<ConstructorPropertiesAnnotatedBean> mutator =
        (SerializingMutator<ConstructorPropertiesAnnotatedBean>)
            Mutators.newFactory()
                .createOrThrow(
                    new TypeHolder<
                        @NotNull ConstructorPropertiesAnnotatedBean>() {}.annotatedType());
    assertThat(mutator.toString())
        .startsWith("[Boolean, Nullable<String>, Integer] -> ConstructorPropertiesAnnotatedBean");
    assertThat(mutator.hasFixedSize()).isFalse();

    PseudoRandom prng = anyPseudoRandom();
    ConstructorPropertiesAnnotatedBean inited = mutator.init(prng);

    ConstructorPropertiesAnnotatedBean mutated = mutator.mutate(inited, prng);
    assertThat(mutated).isNotEqualTo(inited);
  }

  // This class is used to test property name based getter resolution, and, in
  // case it's not available, property type based resolution.
  static class RecursiveTypeBean {
    private final int foo;
    private final RecursiveTypeBean bar;

    public RecursiveTypeBean(int foo) {
      throw new UnsupportedOperationException("This constructor should not be called");
    }

    public RecursiveTypeBean(int foo, RecursiveTypeBean bar) {
      this.foo = foo;
      this.bar = bar;
    }

    public int getFoo() {
      return foo;
    }

    public RecursiveTypeBean getBar() {
      return bar;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      RecursiveTypeBean that = (RecursiveTypeBean) o;
      return foo == that.foo && Objects.equals(bar, that.bar);
    }

    @Override
    public int hashCode() {
      return Objects.hash(foo, bar);
    }

    @Override
    public String toString() {
      return "RecursiveTypeBean{" + "foo=" + foo + ", bar=" + bar + '}';
    }
  }

  @Test
  void testRecursiveTypeBean() {
    SerializingMutator<RecursiveTypeBean> mutator =
        (SerializingMutator<RecursiveTypeBean>)
            Mutators.newFactory()
                .createOrThrow(new TypeHolder<@NotNull RecursiveTypeBean>() {}.annotatedType());
    assertThat(mutator.toString())
        .startsWith(
            "[Integer, Nullable<RecursionBreaking((cycle) -> RecursiveTypeBean)>] ->"
                + " RecursiveTypeBean");
    assertThat(mutator.hasFixedSize()).isFalse();

    PseudoRandom prng = anyPseudoRandom();
    RecursiveTypeBean inited = mutator.init(prng);

    RecursiveTypeBean mutated = mutator.mutate(inited, prng);
    assertThat(mutated).isNotEqualTo(inited);
  }

  static class BeanWithParent extends ConstructorPropertiesAnnotatedBean {
    protected int quz;

    @ConstructorProperties({"foo", "BAR", "baz", "quz"})
    BeanWithParent(boolean a, String b, int c, int q) {
      super(a, b, c);
      this.quz = q;
    }

    public int getQuz() {
      return quz;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      BeanWithParent that = (BeanWithParent) o;
      return quz == that.quz;
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), quz);
    }

    @Override
    public String toString() {
      return "BeanWithParent{"
          + "quz="
          + quz
          + ", foo="
          + isFoo()
          + ", bar='"
          + getBAR()
          + '\''
          + ", baz="
          + getBaz()
          + '}';
    }
  }

  @Test
  void testBeanWithParent() {
    SerializingMutator<BeanWithParent> mutator =
        (SerializingMutator<BeanWithParent>)
            Mutators.newFactory()
                .createOrThrow(new TypeHolder<@NotNull BeanWithParent>() {}.annotatedType());
    assertThat(mutator.toString())
        .startsWith("[Boolean, Nullable<String>, Integer, Integer] -> BeanWithParent");
    assertThat(mutator.hasFixedSize()).isFalse();

    PseudoRandom prng = anyPseudoRandom();
    BeanWithParent inited = mutator.init(prng);

    BeanWithParent mutated = mutator.mutate(inited, prng);
    assertThat(mutated).isNotEqualTo(inited);
  }
}