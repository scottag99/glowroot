/*
 * Copyright 2012-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.weaving;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import org.glowroot.api.MetricName;
import org.glowroot.api.MetricTimer;
import org.glowroot.api.OptionalReturn;
import org.glowroot.api.weaving.Mixin;
import org.glowroot.api.weaving.Pointcut;
import org.glowroot.weaving.AbstractMisc.ExtendsAbstractMisc;
import org.glowroot.weaving.AbstractNotMisc.ExtendsAbstractNotMisc;
import org.glowroot.weaving.AbstractNotMiscWithFinal.ExtendsAbstractNotMiscWithFinal;
import org.glowroot.weaving.SomeAspect.BasicAdvice;
import org.glowroot.weaving.SomeAspect.BasicMiscConstructorAdvice;
import org.glowroot.weaving.SomeAspect.BasicMiscConstructorOnInterfaceImplAdvice;
import org.glowroot.weaving.SomeAspect.BasicWithInnerClassAdvice;
import org.glowroot.weaving.SomeAspect.BasicWithInnerClassArgAdvice;
import org.glowroot.weaving.SomeAspect.BindAutoboxedReturnAdvice;
import org.glowroot.weaving.SomeAspect.BindMethodArgAdvice;
import org.glowroot.weaving.SomeAspect.BindMethodArgArrayAdvice;
import org.glowroot.weaving.SomeAspect.BindMethodNameAdvice;
import org.glowroot.weaving.SomeAspect.BindOptionalPrimitiveReturnAdvice;
import org.glowroot.weaving.SomeAspect.BindOptionalReturnAdvice;
import org.glowroot.weaving.SomeAspect.BindOptionalVoidReturnAdvice;
import org.glowroot.weaving.SomeAspect.BindPrimitiveBooleanTravelerAdvice;
import org.glowroot.weaving.SomeAspect.BindPrimitiveReturnAdvice;
import org.glowroot.weaving.SomeAspect.BindPrimitiveTravelerAdvice;
import org.glowroot.weaving.SomeAspect.BindReturnAdvice;
import org.glowroot.weaving.SomeAspect.BindTargetAdvice;
import org.glowroot.weaving.SomeAspect.BindThrowableAdvice;
import org.glowroot.weaving.SomeAspect.BindTravelerAdvice;
import org.glowroot.weaving.SomeAspect.BrokenAdvice;
import org.glowroot.weaving.SomeAspect.ChangeReturnAdvice;
import org.glowroot.weaving.SomeAspect.CircularClassDependencyAdvice;
import org.glowroot.weaving.SomeAspect.HasString;
import org.glowroot.weaving.SomeAspect.HasStringClassMixin;
import org.glowroot.weaving.SomeAspect.HasStringInterfaceMixin;
import org.glowroot.weaving.SomeAspect.HasStringMultipleMixin;
import org.glowroot.weaving.SomeAspect.InnerMethodAdvice;
import org.glowroot.weaving.SomeAspect.InterfaceAppearsTwiceInHierarchyAdvice;
import org.glowroot.weaving.SomeAspect.MethodArgsDotDotAdvice1;
import org.glowroot.weaving.SomeAspect.MethodArgsDotDotAdvice2;
import org.glowroot.weaving.SomeAspect.MethodArgsDotDotAdvice3;
import org.glowroot.weaving.SomeAspect.MethodReturnCharSequenceAdvice;
import org.glowroot.weaving.SomeAspect.MethodReturnStringAdvice;
import org.glowroot.weaving.SomeAspect.MethodReturnVoidAdvice;
import org.glowroot.weaving.SomeAspect.MultipleMethodsAdvice;
import org.glowroot.weaving.SomeAspect.NonMatchingMethodReturnAdvice;
import org.glowroot.weaving.SomeAspect.NonMatchingMethodReturnAdvice2;
import org.glowroot.weaving.SomeAspect.NonMatchingStaticAdvice;
import org.glowroot.weaving.SomeAspect.NotNestingAdvice;
import org.glowroot.weaving.SomeAspect.NotNestingWithNoIsEnabledAdvice;
import org.glowroot.weaving.SomeAspect.PrimitiveAdvice;
import org.glowroot.weaving.SomeAspect.PrimitiveWithAutoboxAdvice;
import org.glowroot.weaving.SomeAspect.PrimitiveWithWildcardAdvice;
import org.glowroot.weaving.SomeAspect.StaticAdvice;
import org.glowroot.weaving.SomeAspect.StaticBindTargetClassAdvice;
import org.glowroot.weaving.SomeAspect.TestJSRInlinedMethodAdvice;
import org.glowroot.weaving.SomeAspect.TypeNamePatternAdvice;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class WeaverTest {

    // ===================== @IsEnabled =====================

    @Test
    public void shouldExecuteEnabledAdvice() throws Exception {
        // given
        BasicAdvice.resetThreadLocals();
        BasicAdvice.enable();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BasicAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldExecuteEnabledAdviceOnThrow() throws Exception {
        // given
        BasicAdvice.resetThreadLocals();
        BasicAdvice.enable();
        Misc test = newWovenObject(ThrowingMisc.class, Misc.class, BasicAdvice.class);
        // when
        try {
            test.execute1();
        } catch (Throwable t) {
        }
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldNotExecuteDisabledAdvice() throws Exception {
        // given
        BasicAdvice.resetThreadLocals();
        BasicAdvice.disable();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BasicAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldNotExecuteDisabledAdviceOnThrow() throws Exception {
        // given
        BasicAdvice.resetThreadLocals();
        BasicAdvice.disable();
        Misc test = newWovenObject(ThrowingMisc.class, Misc.class, BasicAdvice.class);
        // when
        try {
            test.execute1();
        } catch (Throwable t) {
        }
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(0);
    }

    // ===================== @BindTarget =====================

    @Test
    public void shouldBindTarget() throws Exception {
        // given
        BindTargetAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindTargetAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(BindTargetAdvice.isEnabledTarget.get()).isEqualTo(test);
        assertThat(BindTargetAdvice.onBeforeTarget.get()).isEqualTo(test);
        assertThat(BindTargetAdvice.onReturnTarget.get()).isEqualTo(test);
        assertThat(BindTargetAdvice.onThrowTarget.get()).isNull();
        assertThat(BindTargetAdvice.onAfterTarget.get()).isEqualTo(test);
    }

    @Test
    public void shouldBindTargetOnThrow() throws Exception {
        // given
        BindTargetAdvice.resetThreadLocals();
        Misc test = newWovenObject(ThrowingMisc.class, Misc.class, BindTargetAdvice.class);
        // when
        try {
            test.execute1();
        } catch (Throwable t) {
        }
        // then
        assertThat(BindTargetAdvice.isEnabledTarget.get()).isEqualTo(test);
        assertThat(BindTargetAdvice.onBeforeTarget.get()).isEqualTo(test);
        assertThat(BindTargetAdvice.onReturnTarget.get()).isNull();
        assertThat(BindTargetAdvice.onThrowTarget.get()).isEqualTo(test);
        assertThat(BindTargetAdvice.onAfterTarget.get()).isEqualTo(test);
    }

    // ===================== @BindMethodArg =====================

    @Test
    public void shouldBindMethodArgs() throws Exception {
        // given
        BindMethodArgAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindMethodArgAdvice.class);
        // when
        test.executeWithArgs("one", 2);
        // then
        Object[] parameters = new Object[] {"one", 2};
        assertThat(BindMethodArgAdvice.isEnabledParams.get()).isEqualTo(parameters);
        assertThat(BindMethodArgAdvice.onBeforeParams.get()).isEqualTo(parameters);
        assertThat(BindMethodArgAdvice.onReturnParams.get()).isEqualTo(parameters);
        assertThat(BindMethodArgAdvice.onThrowParams.get()).isNull();
        assertThat(BindMethodArgAdvice.onAfterParams.get()).isEqualTo(parameters);
    }

    @Test
    public void shouldBindMethodArgOnThrow() throws Exception {
        // given
        BindMethodArgAdvice.resetThreadLocals();
        Misc test = newWovenObject(ThrowingMisc.class, Misc.class, BindMethodArgAdvice.class);
        // when
        try {
            test.executeWithArgs("one", 2);
        } catch (Throwable t) {
        }
        // then
        Object[] parameters = new Object[] {"one", 2};
        assertThat(BindMethodArgAdvice.isEnabledParams.get()).isEqualTo(parameters);
        assertThat(BindMethodArgAdvice.onBeforeParams.get()).isEqualTo(parameters);
        assertThat(BindMethodArgAdvice.onReturnParams.get()).isNull();
        assertThat(BindMethodArgAdvice.onThrowParams.get()).isEqualTo(parameters);
        assertThat(BindMethodArgAdvice.onAfterParams.get()).isEqualTo(parameters);
    }

    // ===================== @BindMethodArgArray =====================

    @Test
    public void shouldBindMethodArgArray() throws Exception {
        // given
        BindMethodArgArrayAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindMethodArgArrayAdvice.class);
        // when
        test.executeWithArgs("one", 2);
        // then
        Object[] parameters = new Object[] {"one", 2};
        assertThat(BindMethodArgArrayAdvice.isEnabledParams.get()).isEqualTo(parameters);
        assertThat(BindMethodArgArrayAdvice.onBeforeParams.get()).isEqualTo(parameters);
        assertThat(BindMethodArgArrayAdvice.onReturnParams.get()).isEqualTo(parameters);
        assertThat(BindMethodArgArrayAdvice.onThrowParams.get()).isNull();
        assertThat(BindMethodArgArrayAdvice.onAfterParams.get()).isEqualTo(parameters);
    }

    @Test
    public void shouldBindMethodArgArrayOnThrow() throws Exception {
        // given
        BindMethodArgArrayAdvice.resetThreadLocals();
        Misc test = newWovenObject(ThrowingMisc.class, Misc.class,
                BindMethodArgArrayAdvice.class);
        // when
        try {
            test.executeWithArgs("one", 2);
        } catch (Throwable t) {
        }
        // then
        Object[] parameters = new Object[] {"one", 2};
        assertThat(BindMethodArgArrayAdvice.isEnabledParams.get()).isEqualTo(parameters);
        assertThat(BindMethodArgArrayAdvice.onBeforeParams.get()).isEqualTo(parameters);
        assertThat(BindMethodArgArrayAdvice.onReturnParams.get()).isNull();
        assertThat(BindMethodArgArrayAdvice.onThrowParams.get()).isEqualTo(parameters);
        assertThat(BindMethodArgArrayAdvice.onAfterParams.get()).isEqualTo(parameters);
    }

    // ===================== @BindTraveler =====================

    @Test
    public void shouldBindTraveler() throws Exception {
        // given
        BindTravelerAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindTravelerAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(BindTravelerAdvice.onReturnTraveler.get()).isEqualTo("a traveler");
        assertThat(BindTravelerAdvice.onThrowTraveler.get()).isNull();
        assertThat(BindTravelerAdvice.onAfterTraveler.get()).isEqualTo("a traveler");
    }

    @Test
    public void shouldBindPrimitiveTraveler() throws Exception {
        // given
        BindPrimitiveTravelerAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindPrimitiveTravelerAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(BindPrimitiveTravelerAdvice.onReturnTraveler.get()).isEqualTo(3);
        assertThat(BindPrimitiveTravelerAdvice.onThrowTraveler.get()).isNull();
        assertThat(BindPrimitiveTravelerAdvice.onAfterTraveler.get()).isEqualTo(3);
    }

    @Test
    public void shouldBindPrimitiveBooleanTraveler() throws Exception {
        // given
        BindPrimitiveBooleanTravelerAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class,
                BindPrimitiveBooleanTravelerAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(BindPrimitiveBooleanTravelerAdvice.onReturnTraveler.get()).isEqualTo(true);
        assertThat(BindPrimitiveBooleanTravelerAdvice.onThrowTraveler.get()).isNull();
        assertThat(BindPrimitiveBooleanTravelerAdvice.onAfterTraveler.get()).isEqualTo(true);
    }

    @Test
    public void shouldBindTravelerOnThrow() throws Exception {
        // given
        BindTravelerAdvice.resetThreadLocals();
        Misc test = newWovenObject(ThrowingMisc.class, Misc.class, BindTravelerAdvice.class);
        // when
        try {
            test.execute1();
        } catch (Throwable t) {
        }
        // then
        assertThat(BindTravelerAdvice.onReturnTraveler.get()).isNull();
        assertThat(BindTravelerAdvice.onThrowTraveler.get()).isEqualTo("a traveler");
        assertThat(BindTravelerAdvice.onAfterTraveler.get()).isEqualTo("a traveler");
    }

    // ===================== @BindReturn =====================

    @Test
    public void shouldBindReturn() throws Exception {
        // given
        BindReturnAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindReturnAdvice.class);
        // when
        test.executeWithReturn();
        // then
        assertThat(BindReturnAdvice.returnValue.get()).isEqualTo("xyz");
    }

    @Test
    public void shouldBindPrimitiveReturn() throws Exception {
        // given
        BindPrimitiveReturnAdvice.resetThreadLocals();
        Misc test = newWovenObject(PrimitiveMisc.class, Misc.class,
                BindPrimitiveReturnAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(BindPrimitiveReturnAdvice.returnValue.get()).isEqualTo(4);
    }

    @Test
    public void shouldBindAutoboxedReturn() throws Exception {
        // given
        BindAutoboxedReturnAdvice.resetThreadLocals();
        Misc test = newWovenObject(PrimitiveMisc.class, Misc.class,
                BindAutoboxedReturnAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(BindAutoboxedReturnAdvice.returnValue.get()).isEqualTo(4);
    }

    // ===================== @BindOptionalReturn =====================

    @Test
    public void shouldBindOptionalReturn() throws Exception {
        // given
        BindOptionalReturnAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindOptionalReturnAdvice.class,
                OptionalReturn.class);
        // when
        test.executeWithReturn();
        // then
        assertThat(BindOptionalReturnAdvice.returnValue.get().isVoid()).isFalse();
        assertThat(BindOptionalReturnAdvice.returnValue.get().getValue()).isEqualTo("xyz");
    }

    @Test
    public void shouldBindOptionalVoidReturn() throws Exception {
        // given
        BindOptionalVoidReturnAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindOptionalVoidReturnAdvice.class,
                OptionalReturn.class);
        // when
        test.execute1();
        // then
        assertThat(BindOptionalVoidReturnAdvice.returnValue.get().isVoid()).isTrue();
    }

    @Test
    public void shouldBindOptionalPrimitiveReturn() throws Exception {
        // given
        BindOptionalPrimitiveReturnAdvice.resetThreadLocals();
        Misc test = newWovenObject(PrimitiveMisc.class, Misc.class,
                BindOptionalPrimitiveReturnAdvice.class, OptionalReturn.class);
        // when
        test.execute1();
        // then
        assertThat(BindOptionalPrimitiveReturnAdvice.returnValue.get().isVoid()).isFalse();
        assertThat(BindOptionalPrimitiveReturnAdvice.returnValue.get().getValue())
                .isEqualTo(Integer.valueOf(4));
    }

    // ===================== @BindThrowable =====================

    @Test
    public void shouldBindThrowable() throws Exception {
        // given
        BindThrowableAdvice.resetThreadLocals();
        Misc test = newWovenObject(ThrowingMisc.class, Misc.class, BindThrowableAdvice.class);
        // when
        try {
            test.execute1();
        } catch (Throwable t) {
        }
        // then
        assertThat(BindThrowableAdvice.throwable.get()).isNotNull();
    }

    // ===================== @BindMethodName =====================

    @Test
    public void shouldBindMethodName() throws Exception {
        // given
        BindMethodNameAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BindMethodNameAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(BindMethodNameAdvice.isEnabledMethodName.get()).isEqualTo("execute1");
        assertThat(BindMethodNameAdvice.onBeforeMethodName.get()).isEqualTo("execute1");
        assertThat(BindMethodNameAdvice.onReturnMethodName.get()).isEqualTo("execute1");
        assertThat(BindMethodNameAdvice.onThrowMethodName.get()).isNull();
        assertThat(BindMethodNameAdvice.onAfterMethodName.get()).isEqualTo("execute1");
    }

    // ===================== change return value =====================

    @Test
    public void shouldChangeReturnValue() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, ChangeReturnAdvice.class);
        // when
        CharSequence returnValue = test.executeWithReturn();
        // then
        assertThat(returnValue).isEqualTo("modified xyz");
    }

    // ===================== inheritance =====================

    @Test
    public void shouldNotWeaveIfDoesNotOverrideMatch() throws Exception {
        // given
        BasicAdvice.resetThreadLocals();
        Misc2 test = newWovenObject(BasicMisc.class, Misc2.class, BasicAdvice.class);
        // when
        test.execute2();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(0);
    }

    // ===================== methodArgs '..' =====================

    @Test
    public void shouldMatchMethodArgsDotDot1() throws Exception {
        // given
        MethodArgsDotDotAdvice1.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, MethodArgsDotDotAdvice1.class);
        // when
        test.executeWithArgs("one", 2);
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldMatchMethodArgsDotDot2() throws Exception {
        // given
        MethodArgsDotDotAdvice2.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, MethodArgsDotDotAdvice2.class);
        // when
        test.executeWithArgs("one", 2);
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldMatchMethodArgsDotDot3() throws Exception {
        // given
        MethodArgsDotDotAdvice3.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, MethodArgsDotDotAdvice3.class);
        // when
        test.executeWithArgs("one", 2);
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
    }

    // ===================== @Mixin =====================

    @Test
    public void shouldMixinToClass() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, HasStringClassMixin.class,
                HasString.class);
        // when
        ((HasString) test).setString("another value");
        // then
        assertThat(((HasString) test).getString()).isEqualTo("another value");
    }

    @Test
    public void shouldMixinToInterface() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, HasStringInterfaceMixin.class,
                HasString.class);
        // when
        ((HasString) test).setString("another value");
        // then
        assertThat(((HasString) test).getString()).isEqualTo("another value");
    }

    @Test
    public void shouldMixinOnlyOnce() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, HasStringMultipleMixin.class,
                HasString.class);
        // when
        ((HasString) test).setString("another value");
        // then
        assertThat(((HasString) test).getString()).isEqualTo("another value");
    }

    @Test
    public void shouldMixinAndCallInitExactlyOnce() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, HasStringClassMixin.class,
                HasString.class);
        // when
        // then
        assertThat(((HasString) test).getString()).isEqualTo("a string");
    }

    // ===================== @Pointcut.nestable =====================

    @Test
    public void shouldNotNestPointcuts() throws Exception {
        // given
        NotNestingAdvice.resetThreadLocals();
        Misc test = newWovenObject(NestingMisc.class, Misc.class, NotNestingAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(1);
        assertThat(test.executeWithReturn()).isEqualTo("yes");
    }

    @Test
    public void shouldNotNestPointcuts2() throws Exception {
        // given
        NotNestingAdvice.resetThreadLocals();
        Misc test = newWovenObject(NestingMisc.class, Misc.class, NotNestingAdvice.class);
        // when
        test.execute1();
        test.execute1();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(2);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(2);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(2);
        assertThat(test.executeWithReturn()).isEqualTo("yes");
    }

    @Test
    public void shouldNotNestPointcuts3() throws Exception {
        // given
        NotNestingAdvice.resetThreadLocals();
        Misc test = newWovenObject(NestingAnotherMisc.class, Misc.class, NotNestingAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(1);
        assertThat(test.executeWithReturn()).isEqualTo("yes");
    }

    @Test
    public void shouldNestPointcuts() throws Exception {
        // given
        BasicAdvice.resetThreadLocals();
        Misc test = newWovenObject(NestingMisc.class, Misc.class, BasicAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(2);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(2);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(2);
    }

    @Test
    public void shouldNotNestPointcutsEvenWithNoIsEnabled() throws Exception {
        // given
        NotNestingAdvice.resetThreadLocals();
        Misc test = newWovenObject(NestingMisc.class, Misc.class,
                NotNestingWithNoIsEnabledAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(1);
        assertThat(test.executeWithReturn()).isEqualTo("yes");
    }

    // ===================== @Pointcut.innerMethod =====================

    @Test
    public void shouldWrapInMarkerMethod() throws Exception {
        // given
        Misc test = newWovenObject(InnerMethodMisc.class, Misc.class, InnerMethodAdvice.class);
        // when
        CharSequence methodName = test.executeWithReturn();
        // then
        assertThat(methodName).isNotNull();
        assertThat(methodName.toString())
                .matches("executeWithReturn\\$glowroot\\$metric\\$abc\\$xyz\\$\\d+");
    }

    // ===================== static pointcuts =====================

    @Test
    public void shouldWeaveStaticMethod() throws Exception {
        // given
        StaticAdvice.resetThreadLocals();
        StaticAdvice.enable();
        Misc test = newWovenObject(StaticMisc.class, Misc.class, StaticAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldWeaveStaticMethodBindTargetClass() throws Exception {
        // given
        StaticBindTargetClassAdvice.resetThreadLocals();
        Misc test = newWovenObject(StaticMisc.class, Misc.class,
                StaticBindTargetClassAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(StaticBindTargetClassAdvice.onBeforeTarget.get().getName())
                .isEqualTo(StaticMisc.class.getName());
    }
    // ===================== primitive args =====================

    @Test
    public void shouldWeaveMethodWithPrimitiveArgs() throws Exception {
        // given
        PrimitiveAdvice.resetThreadLocals();
        PrimitiveAdvice.enable();
        Misc test = newWovenObject(PrimitiveMisc.class, Misc.class, PrimitiveAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(1);
    }

    // ===================== wildcard args =====================

    @Test
    public void shouldWeaveMethodWithWildcardArgs() throws Exception {
        // given
        PrimitiveWithWildcardAdvice.resetThreadLocals();
        Misc test = newWovenObject(PrimitiveMisc.class, Misc.class,
                PrimitiveWithWildcardAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.enabledCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
    }

    // ===================== type name pattern =====================

    @Test
    public void shouldWeaveTypeWithNamePattern() throws Exception {
        // given
        TypeNamePatternAdvice.resetThreadLocals();
        Misc test = newWovenObject(PrimitiveMisc.class, Misc.class, TypeNamePatternAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.enabledCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
    }

    // ===================== autobox args =====================

    @Test
    public void shouldWeaveMethodWithAutoboxArgs() throws Exception {
        // given
        PrimitiveWithAutoboxAdvice.resetThreadLocals();
        Misc test = newWovenObject(PrimitiveMisc.class, Misc.class,
                PrimitiveWithAutoboxAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.enabledCount.get()).isEqualTo(1);
    }

    // ===================== return type matching =====================

    @Test
    public void shouldMatchMethodReturningVoid() throws Exception {
        // given
        MethodReturnVoidAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, MethodReturnVoidAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldMatchMethodReturningCharSequence() throws Exception {
        // given
        MethodReturnStringAdvice.resetThreadLocals();
        Misc test =
                newWovenObject(BasicMisc.class, Misc.class, MethodReturnCharSequenceAdvice.class);
        // when
        test.executeWithReturn();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldNotMatchMethodReturningString() throws Exception {
        // given
        MethodReturnStringAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, MethodReturnStringAdvice.class);
        // when
        test.executeWithReturn();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldNotMatchMethodBasedOnReturnType() throws Exception {
        // given
        NonMatchingMethodReturnAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class,
                NonMatchingMethodReturnAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldNotMatchMethodBasedOnReturnType2() throws Exception {
        // given
        NonMatchingMethodReturnAdvice2.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class,
                NonMatchingMethodReturnAdvice2.class);
        // when
        test.executeWithReturn();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(0);
    }

    // ===================== constructor =====================

    @Test
    // note: constructor pointcuts do not currently support @OnBefore
    public void shouldHandleConstructorPointcut() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BasicMiscConstructorAdvice.class);
        // reset thread locals after instantiated BasicMisc, to avoid counting that constructor call
        BasicMiscConstructorAdvice.resetThreadLocals();
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.enabledCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    // note: constructor pointcuts do not currently support @OnBefore
    public void shouldHandleConstructorOnInterfaceImplPointcut() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class,
                BasicMiscConstructorOnInterfaceImplAdvice.class);
        // reset thread locals after instantiated BasicMisc, to avoid counting that constructor call
        BasicMiscConstructorOnInterfaceImplAdvice.resetThreadLocals();
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.enabledCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldHandleInheritedMethodFulfillingAnInterface() throws Exception {
        // given
        BasicAdvice.resetThreadLocals();
        BasicAdvice.enable();
        Misc test = newWovenObject(ExtendsAbstractNotMisc.class, Misc.class, BasicAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldHandleGracefullyInheritedFinalMethodFulfillingAnInterface() throws Exception {
        // given
        BasicAdvice.resetThreadLocals();
        BasicAdvice.enable();
        Misc test = newWovenObject(ExtendsAbstractNotMiscWithFinal.class, Misc.class,
                BasicAdvice.class);
        // when
        test.execute1();
        // then
        // TODO check for warning message
    }

    @Test
    public void shouldHandleInnerClassArg() throws Exception {
        // given
        BasicWithInnerClassArgAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BasicWithInnerClassArgAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.enabledCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldHandleInnerClass() throws Exception {
        // given
        BasicWithInnerClassAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.InnerMisc.class, Misc.class,
                BasicWithInnerClassAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.enabledCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(1);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(1);
    }

    @Test
    public void shouldHandlePointcutWithMultipleMethods() throws Exception {
        // given
        MultipleMethodsAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, MultipleMethodsAdvice.class);
        // when
        test.execute1();
        test.executeWithArgs("one", 2);
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(2);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(2);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(2);
    }

    @Test
    public void shouldNotTryToWeaveNativeMethods() throws Exception {
        // given
        // when
        newWovenObject(NativeMisc.class, Misc.class, BasicAdvice.class);
        // then should not bomb
    }

    @Test
    public void shouldNotTryToWeaveAbstractMethods() throws Exception {
        // given
        Misc test = newWovenObject(ExtendsAbstractMisc.class, Misc.class, BasicAdvice.class);
        // when
        test.execute1();
        // then should not bomb
    }

    @Test
    public void shouldNotDisruptInnerTryCatch() throws Exception {
        // given
        Misc test = newWovenObject(InnerTryCatchMisc.class, Misc.class, BasicAdvice.class,
                HasString.class);
        // when
        test.execute1();
        // then
        assertThat(test.executeWithReturn()).isEqualTo("caught");
    }

    @Test
    public void shouldPayAttentionToStaticKeyword() throws Exception {
        // given
        NonMatchingStaticAdvice.resetThreadLocals();
        Misc test = newWovenObject(BasicMisc.class, Misc.class, NonMatchingStaticAdvice.class);
        // when
        test.execute1();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onReturnCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onThrowCount.get()).isEqualTo(0);
        assertThat(SomeAspect.onAfterCount.get()).isEqualTo(0);
    }

    @Test
    public void shouldNotBomb() throws Exception {
        // given
        Misc test = newWovenObject(BasicMisc.class, Misc.class, BrokenAdvice.class);
        // when
        test.executeWithArgs("one", 2);
        // then should not bomb
    }

    @Test
    public void shouldNotBomb2() throws Exception {
        // given
        Misc test = newWovenObject(AccessibilityMisc.class, Misc.class, BasicAdvice.class);
        // when
        test.execute1();
        // then should not bomb
    }

    @Test
    // weaving an interface method that references a concrete class that implements that interface
    // is supported
    public void shouldHandleCircularDependency() throws Exception {
        // given
        CircularClassDependencyAdvice.resetThreadLocals();
        // when
        newWovenObject(BasicMisc.class, Misc.class, CircularClassDependencyAdvice.class);
        // then should not bomb
    }

    @Test
    // weaving an interface method that appears twice in a given class hierarchy should only weave
    // the method once
    public void shouldHandleInterfaceThatAppearsTwiceInHierarchy() throws Exception {
        // given
        InterfaceAppearsTwiceInHierarchyAdvice.resetThreadLocals();
        // when
        Misc test = newWovenObject(SubBasicMisc.class, Misc.class,
                InterfaceAppearsTwiceInHierarchyAdvice.class);
        test.execute1();
        // then
        assertThat(SomeAspect.onBeforeCount.get()).isEqualTo(1);
    }

    @Test
    // test weaving against JSR bytecode that ends up being inlined via JSRInlinerAdapter
    public void shouldWeaveJsrInlinedBytecode() throws Exception {
        Misc test = newWovenObject(JsrInlinedMethodMisc.class, Misc.class,
                TestJSRInlinedMethodAdvice.class);
        test.executeWithReturn();
    }

    public static <S, T extends S> S newWovenObject(Class<T> implClass, Class<S> bridgeClass,
            Class<?> adviceClass, Class<?>... extraBridgeClasses) throws Exception {

        IsolatedWeavingClassLoader.Builder loader = IsolatedWeavingClassLoader.builder();
        Pointcut pointcut = adviceClass.getAnnotation(Pointcut.class);
        if (pointcut != null) {
            loader.setAdvisors(ImmutableList.of(Advice.from(pointcut, adviceClass, false)));
        }
        Mixin mixin = adviceClass.getAnnotation(Mixin.class);
        if (mixin != null) {
            loader.setMixinTypes(ImmutableList.of(MixinType.from(mixin, adviceClass)));
        }
        loader.setMetricTimerService(NopMetricTimerService.INSTANCE);
        // adviceClass is passed as bridgeable so that the static threadlocals will be accessible
        // for test verification
        loader.addBridgeClasses(bridgeClass, adviceClass);
        loader.addBridgeClasses(extraBridgeClasses);
        return loader.build().newInstance(implClass, bridgeClass);
    }

    private static class NopMetricTimerService implements MetricTimerService {
        private static final NopMetricTimerService INSTANCE = new NopMetricTimerService();
        @Override
        public MetricName getMetricName(String name) {
            return NopMetricName.INSTANCE;
        }
        @Override
        public MetricTimer startMetricTimer(MetricName metricName) {
            return NopMetricTimer.INSTANCE;
        }
    }

    private static class NopMetricName implements MetricName {
        private static final NopMetricName INSTANCE = new NopMetricName();
    }

    private static class NopMetricTimer implements MetricTimer {
        private static final NopMetricTimer INSTANCE = new NopMetricTimer();
        @Override
        public void stop() {}
    }
}
