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

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.security.CodeSource;
import java.util.List;
import java.util.Set;

import checkers.igj.quals.ReadOnly;
import checkers.nullness.quals.MonotonicNonNull;
import checkers.nullness.quals.Nullable;
import checkers.nullness.quals.RequiresNonNull;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import dataflow.quals.Pure;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.RemappingMethodAdapter;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.glowroot.weaving.ParsedTypeCache.ParseContext;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.glowroot.common.Nullness.castNonNull;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_NATIVE;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ASM4;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
class WeavingClassVisitor extends ClassVisitor {

    private static final Logger logger = LoggerFactory.getLogger(WeavingClassVisitor.class);

    // this field is just a @NonNull version of the field with the same name in the super class to
    // help with null flow analysis
    private final ClassVisitor cv;

    private final ImmutableList<MixinType> mixinTypes;
    @ReadOnly
    private final Iterable<Advice> advisors;
    @Nullable
    private final ClassLoader loader;
    private final ParsedTypeCache parsedTypeCache;
    @Nullable
    private final CodeSource codeSource;
    private final boolean metricWrapperMethodsDisabled;

    private ImmutableList<AdviceMatcher> adviceMatchers = ImmutableList.of();
    private ImmutableList<MixinType> matchedMixinTypes = ImmutableList.of();
    @MonotonicNonNull
    private Type type;
    @MonotonicNonNull
    private List<ParsedType> superHierarchy;
    @MonotonicNonNull
    private List<ParsedType> interfaceHierarchy;

    private int innerMethodCounter;
    private boolean nothingAtAllToWeave;

    private ParsedType./*@MonotonicNonNull*/Builder parsedTypeBuilder;

    public WeavingClassVisitor(ClassVisitor cv, ImmutableList<MixinType> mixinTypes,
            @ReadOnly Iterable<Advice> advisors, @Nullable ClassLoader loader,
            ParsedTypeCache parsedTypeCache, @Nullable CodeSource codeSource,
            boolean metricWrapperMethodsDisabled) {
        super(ASM4, cv);
        this.cv = cv;
        this.mixinTypes = mixinTypes;
        this.advisors = advisors;
        this.loader = loader;
        this.parsedTypeCache = parsedTypeCache;
        this.codeSource = codeSource;
        this.metricWrapperMethodsDisabled = metricWrapperMethodsDisabled;
    }

    @Override
    public void visit(int version, int access, String name, @Nullable String signature,
            @Nullable String superName, String/*@Nullable*/[] interfaceNamesNullable) {

        String[] interfaceNames = interfaceNamesNullable == null ? new String[0]
                : interfaceNamesNullable;
        parsedTypeBuilder = ParsedType.builder(Modifier.isInterface(access),
                TypeNames.fromInternal(name), TypeNames.fromInternal(superName),
                TypeNames.fromInternal(interfaceNames));
        if (Modifier.isInterface(access)) {
            // interfaces never get woven
            nothingAtAllToWeave = true;
            return;
        }
        type = Type.getObjectType(name);
        ParseContext parseContext = new ParseContext(type.getClassName(), codeSource);
        superHierarchy = parsedTypeCache.getTypeHierarchy(TypeNames.fromInternal(superName),
                loader, parseContext);
        interfaceHierarchy = getInterfaceHierarchy(interfaceNames);
        // it's ok if there are duplicates in the superTypes list (e.g. an interface that appears
        // twice in a type hierarchy), it's rare, dups don't cause an issue for callers, and so it
        // doesn't seem worth the (minor) performance hit to de-dup every time
        Iterable<ParsedType> superTypes = Iterables.concat(superHierarchy, interfaceHierarchy);
        adviceMatchers = getAdviceMatchers(type, superTypes);
        matchedMixinTypes = getMatchedMixinTypes(type, superTypes);
        if (adviceMatchers.isEmpty() && matchedMixinTypes.isEmpty()) {
            nothingAtAllToWeave = true;
            return;
        }
        logger.debug("visit(): adviceMatchers={}", adviceMatchers);
        super.visit(version, access, name, signature, superName,
                getInterfacesIncludingMixins(interfaceNames, matchedMixinTypes));
    }

    @Override
    @Nullable
    public MethodVisitor visitMethod(int access, String name, String desc,
            @Nullable String signature, String/*@Nullable*/[] exceptions) {
        // visit() must be called
        checkNotNull(parsedTypeBuilder, "Call to visit() is required");
        ParsedMethod parsedMethod = null;
        if ((access & (ACC_NATIVE | ACC_SYNTHETIC)) == 0) {
            // don't add native or synthetic methods to the parsed type model
            ImmutableList<String> exceptionList = exceptions == null
                    ? ImmutableList.<String>of() : ImmutableList.copyOf(exceptions);
            parsedMethod = parsedTypeBuilder.addParsedMethod(access, name, desc, signature,
                    exceptionList);
        }
        if (nothingAtAllToWeave) {
            // no need to pass method on to class writer
            return null;
        }
        // type can be null, but not if nothingAtAllToWeave is false
        checkNotNull(type, "Call to visit() is required");
        if (parsedMethod == null || Modifier.isAbstract(access)) {
            // don't try to weave abstract, native and synthetic methods
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
        List<Advice> matchingAdvisors = getMatchingAdvisors(access, parsedMethod);
        if (name.equals("<init>") && !matchedMixinTypes.isEmpty()) {
            return visitInitWithMixin(access, name, desc, signature, exceptions, matchingAdvisors);
        }
        if (matchingAdvisors.isEmpty()) {
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
        return visitMethodWithAdvice(access, name, desc, signature, exceptions, matchingAdvisors);
    }

    @Override
    public void visitEnd() {
        checkNotNull(parsedTypeBuilder, "Call to visit() is required");
        ParsedType parsedType = parsedTypeBuilder.build();
        parsedTypeCache.add(parsedType, loader);
        if (nothingAtAllToWeave) {
            return;
        }
        // type, superHierarchy and interfaceHierarchy can be null, but not if nothingAtAllToWeave
        // is false
        checkNotNull(type, "Call to visit() is required");
        checkNotNull(superHierarchy, "Call to visit() is required");
        checkNotNull(interfaceHierarchy, "Call to visit() is required");
        for (MixinType mixinType : matchedMixinTypes) {
            addMixin(mixinType);
        }
        handleInheritedMethodsFulfillingAnInterface(parsedType);
    }

    boolean isNothingAtAllToWeave() {
        return nothingAtAllToWeave;
    }

    @RequiresNonNull("type")
    private List<ParsedType> getInterfaceHierarchy(String[] interfaceNames) {
        List<ParsedType> superTypes = Lists.newArrayList();
        ParseContext parseContext = new ParseContext(type.getClassName(), codeSource);
        for (String interfaceName : interfaceNames) {
            superTypes.addAll(parsedTypeCache.getTypeHierarchy(
                    TypeNames.fromInternal(interfaceName), loader, parseContext));
        }
        return superTypes;
    }

    private ImmutableList<AdviceMatcher> getAdviceMatchers(Type type,
            Iterable<ParsedType> superTypes) {
        ImmutableList.Builder<AdviceMatcher> adviceMatchersBuilder = ImmutableList.builder();
        for (Advice advice : advisors) {
            AdviceMatcher adviceMatcher = new AdviceMatcher(advice, type, superTypes);
            if (adviceMatcher.isClassLevelMatch()) {
                adviceMatchersBuilder.add(adviceMatcher);
            }
        }
        return adviceMatchersBuilder.build();
    }

    private ImmutableList<MixinType> getMatchedMixinTypes(Type type,
            Iterable<ParsedType> superTypes) {
        ImmutableList.Builder<MixinType> matchedMixinTypesBuilder = ImmutableList.builder();
        for (MixinType mixinType : mixinTypes) {
            if (MixinMatcher.isMatch(mixinType, type, superTypes)) {
                matchedMixinTypesBuilder.add(mixinType);
            }
        }
        return matchedMixinTypesBuilder.build();
    }

    private static String[] getInterfacesIncludingMixins(String[] interfaceNames,
            ImmutableList<MixinType> matchedMixinTypes) {
        if (matchedMixinTypes.isEmpty()) {
            return interfaceNames;
        }
        Set<String> interfacesIncludingMixins = Sets.newHashSet(interfaceNames);
        for (MixinType matchedMixinType : matchedMixinTypes) {
            for (Class<?> mixinInterface : matchedMixinType.getInterfaces()) {
                interfacesIncludingMixins.add(Type.getInternalName(mixinInterface));
            }
        }
        return Iterables.toArray(interfacesIncludingMixins, String.class);
    }

    @RequiresNonNull("parsedTypeBuilder")
    private List<Advice> getMatchingAdvisors(int access, ParsedMethod parsedMethod) {
        List<Advice> matchingAdvisors = Lists.newArrayList();
        for (AdviceMatcher adviceMatcher : adviceMatchers) {
            if (adviceMatcher.isMethodLevelMatch(access, parsedMethod)) {
                matchingAdvisors.add(adviceMatcher.getAdvice());
                if (adviceMatcher.getAdvice().isReweavable()) {
                    parsedTypeBuilder.setHasReweavableAdvice(true);
                }
            }
        }
        return matchingAdvisors;
    }

    @RequiresNonNull("type")
    private MethodVisitor visitInitWithMixin(int access, String name, String desc,
            @Nullable String signature, String/*@Nullable*/[] exceptions,
            List<Advice> matchingAdvisors) {
        MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
        castNonNull(mv);
        mv = new InitMixins(mv, access, name, desc, matchedMixinTypes, type);
        for (Advice advice : matchingAdvisors) {
            if (advice.getPointcut().metricName().length() != 0) {
                logger.warn("cannot add metrics to <clinit> or <init> methods at this time");
                break;
            }
        }
        return new WeavingMethodVisitor(mv, access, name, desc, type, matchingAdvisors);
    }

    @RequiresNonNull("type")
    private MethodVisitor visitMethodWithAdvice(int access, String name, String desc,
            @Nullable String signature, String/*@Nullable*/[] exceptions,
            List<Advice> matchingAdvisors) {
        if (metricWrapperMethodsDisabled) {
            MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
            castNonNull(mv);
            return new WeavingMethodVisitor(mv, access, name, desc, type, matchingAdvisors);
        } else {
            String innerWrappedName = wrapWithSyntheticMetricMarkerMethods(access, name, desc,
                    signature, exceptions, matchingAdvisors);
            String methodName = name;
            int methodAccess = access;
            if (innerWrappedName != null) {
                methodName = innerWrappedName;
                methodAccess = ACC_PRIVATE + ACC_FINAL + (access & ACC_STATIC);
            }
            MethodVisitor mv =
                    cv.visitMethod(methodAccess, methodName, desc, signature, exceptions);
            castNonNull(mv);
            return new WeavingMethodVisitor(mv, methodAccess, methodName, desc, type,
                    matchingAdvisors);
        }
    }

    // returns null if no synthetic metric marker methods were needed
    @RequiresNonNull("type")
    @Nullable
    private String wrapWithSyntheticMetricMarkerMethods(int outerAccess, String outerName,
            String desc, @Nullable String signature, String/*@Nullable*/[] exceptions,
            List<Advice> matchingAdvisors) {
        int innerAccess = ACC_PRIVATE + ACC_FINAL + (outerAccess & ACC_STATIC);
        boolean first = true;
        String currMethodName = outerName;
        for (Advice advice : matchingAdvisors) {
            String metricName = advice.getPointcut().metricName();
            if (metricName.length() == 0) {
                continue;
            }
            String nextMethodName = outerName + "$glowroot$metric$" + metricName.replace(' ', '$')
                    + '$' + innerMethodCounter++;
            int access = first ? outerAccess : innerAccess;
            MethodVisitor mv = cv.visitMethod(access, currMethodName, desc, signature, exceptions);
            castNonNull(mv);
            GeneratorAdapter mg = new GeneratorAdapter(mv, access, nextMethodName, desc);
            if (!Modifier.isStatic(outerAccess)) {
                mg.loadThis();
                mg.loadArgs();
                mg.invokeVirtual(type, new Method(nextMethodName, desc));
            } else {
                mg.loadArgs();
                mg.invokeStatic(type, new Method(nextMethodName, desc));
            }
            mg.returnValue();
            mg.endMethod();
            currMethodName = nextMethodName;
            first = false;
        }
        return first ? null : currMethodName;
    }

    @RequiresNonNull("type")
    private void addMixin(MixinType mixinType) {
        ClassReader cr;
        try {
            cr = new ClassReader(mixinType.getImplementation().getName());
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return;
        }
        ClassNode cn = new ClassNode();
        cr.accept(cn, ClassReader.SKIP_FRAMES);
        // SuppressWarnings because generics are explicitly removed from asm binaries
        // see http://forge.ow2.org/tracker/?group_id=23&atid=100023&func=detail&aid=316377
        @SuppressWarnings("unchecked")
        List<FieldNode> fieldNodes = cn.fields;
        for (FieldNode fieldNode : fieldNodes) {
            fieldNode.accept(this);
        }
        // SuppressWarnings because generics are explicitly removed from asm binaries
        @SuppressWarnings("unchecked")
        List<MethodNode> methodNodes = cn.methods;
        for (MethodNode mn : methodNodes) {
            if (mn.name.equals("<init>")) {
                continue;
            }
            // SuppressWarnings because generics are explicitly removed from asm binaries
            @SuppressWarnings("unchecked")
            String[] exceptions = Iterables.toArray(mn.exceptions, String.class);
            MethodVisitor mv = cv.visitMethod(mn.access, mn.name, mn.desc, mn.signature,
                    exceptions);
            castNonNull(mv);
            mn.accept(new RemappingMethodAdapter(mn.access, mn.desc, mv,
                    new SimpleRemapper(cn.name, type.getInternalName())));
        }
    }

    // handle inherited methods which are used to fulfill an interface contract, so may need to be
    // woven now that they are part of the interface contract, but are in a super class so its too
    // late to weave them
    //
    // * look at interfaces added to this class (and not its super class)
    // * look at methods for those interfaces that are implemented in the super class but not
    // * implemented in this class
    // * check if those methods need weaving if they were implemented in this class
    //
    // these methods should be woven, so override these methods in this class to simply call
    // super, and then weave them as normal
    @RequiresNonNull({"type", "interfaceHierarchy", "superHierarchy", "parsedTypeBuilder"})
    private void handleInheritedMethodsFulfillingAnInterface(ParsedType parsedType) {
        Set<ParsedMethod> inheritedMethods = Sets.newHashSet();
        for (ParsedType interfaceType : interfaceHierarchy) {
            if (superHierarchy.contains(interfaceType)) {
                // this interface's methods have already been checked/dealt with a the super class
                continue;
            }
            for (ParsedMethod method : interfaceType.getMethods()) {
                if (parsedType.getMethod(method) != null) {
                    continue;
                }
                // this interface method was not implemented in this class
                // check to see if it was implemented in a super class
                for (ParsedType inheritedType : superHierarchy) {
                    if (inheritedType.isInterface()) {
                        continue;
                    }
                    ParsedMethod inheritedMethod = inheritedType.getMethod(method);
                    if (inheritedMethod == null
                            || Modifier.isAbstract(inheritedMethod.getModifiers())) {
                        continue;
                    }
                    if (inheritedMethods.contains(inheritedMethod)) {
                        // already added from another super type
                        continue;
                    }
                    List<Advice> matchingAdvisors =
                            getMatchingAdvisors(inheritedMethod.getModifiers(), inheritedMethod);
                    if (matchingAdvisors.isEmpty()) {
                        // doesn't need to be woven
                        continue;
                    }
                    if (inheritedMethod.isFinal()) {
                        logWarningCannotOverrideAndWeaveInheritedFinalMethod(inheritedType,
                                inheritedMethod, parsedType, interfaceType);
                        continue;
                    }
                    // keep track so
                    inheritedMethods.add(inheritedMethod);
                    overrideAndWeaveInheritedMethod(parsedType, inheritedMethod, matchingAdvisors);
                }
            }
        }
    }

    private void logWarningCannotOverrideAndWeaveInheritedFinalMethod(ParsedType inheritedType,
            ParsedMethod inheritedMethod, ParsedType parsedType, ParsedType interfaceType) {
        StringBuilder inheritedMethodDisplay = new StringBuilder();
        inheritedMethodDisplay.append(inheritedMethod.getReturnTypeName());
        inheritedMethodDisplay.append(' ');
        inheritedMethodDisplay.append(inheritedMethod.getName());
        inheritedMethodDisplay.append('(');
        inheritedMethodDisplay.append(
                Joiner.on(", ").join(inheritedMethod.getArgTypeNames()));
        inheritedMethodDisplay.append(')');
        logger.warn("cannot weave final methods that are then inherited to fulfill an interface:"
                + " superClass={}, superClassMethod={}, subClass={}, subClassInterface={}",
                inheritedType.getName(), inheritedMethodDisplay, parsedType.getName(),
                interfaceType.getName());
    }

    @RequiresNonNull({"type"})
    private void overrideAndWeaveInheritedMethod(ParsedType parsedType,
            ParsedMethod inheritedMethod, List<Advice> matchingAdvisors) {
        String[] exceptions = Iterables.toArray(inheritedMethod.getExceptions(), String.class);
        MethodVisitor mv = visitMethodWithAdvice(ACC_PUBLIC, inheritedMethod.getName(),
                inheritedMethod.getDesc(), inheritedMethod.getSignature(), exceptions,
                matchingAdvisors);
        castNonNull(mv);
        GeneratorAdapter mg = new GeneratorAdapter(mv, ACC_PUBLIC, inheritedMethod.getName(),
                inheritedMethod.getDesc());
        mg.visitCode();
        mg.loadThis();
        mg.loadArgs();
        String superName = parsedType.getSuperName();
        Type superType;
        if (superName == null) {
            superType = Type.getType(Object.class);
        } else {
            superType = Type.getType(TypeNames.toInternal(superName));
        }
        // method is called invokeConstructor, but should really be called invokeSpecial
        mg.invokeConstructor(superType,
                new Method(inheritedMethod.getName(), inheritedMethod.getDesc()));
        mg.returnValue();
        mg.endMethod();
    }

    @Override
    @Pure
    public String toString() {
        // not including fields that are just direct copies from Weaver
        ToStringHelper toStringHelper = Objects.toStringHelper(this)
                .add("codeSource", codeSource)
                .add("adviceMatchers", adviceMatchers)
                .add("matchedMixinTypes", matchedMixinTypes)
                .add("type", type)
                .add("innerMethodCounter", innerMethodCounter)
                .add("nothingAtAllToWeave", nothingAtAllToWeave);
        if (parsedTypeBuilder != null) {
            toStringHelper.add("parsedType", parsedTypeBuilder.build());
        }
        return toStringHelper.toString();
    }

    private static class InitMixins extends AdviceAdapter {

        @ReadOnly
        private final List<MixinType> matchedMixinTypes;
        private final Type type;
        private boolean cascadingConstructor;

        InitMixins(MethodVisitor mv, int access, String name, String desc,
                @ReadOnly List<MixinType> matchedMixinTypes, Type type) {
            super(ASM4, mv, access, name, desc);
            this.matchedMixinTypes = matchedMixinTypes;
            this.type = type;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            if (name.equals("<init>") && owner.equals(type.getInternalName())) {
                cascadingConstructor = true;
            }
            super.visitMethodInsn(opcode, owner, name, desc);
        }

        @Override
        protected void onMethodExit(int opcode) {
            if (cascadingConstructor) {
                // need to call MixinInit exactly once, so don't call MixinInit at end of cascading
                // constructors
                return;
            }
            for (MixinType mixinType : matchedMixinTypes) {
                String initMethodName = mixinType.getInitMethodName();
                if (initMethodName != null) {
                    loadThis();
                    invokeVirtual(type, new Method(initMethodName, "()V"));
                }
            }
        }
    }
}
