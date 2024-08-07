package ai.timefold.solver.core.impl.domain.common.accessor.gizmo;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import ai.timefold.solver.core.impl.domain.common.accessor.MemberAccessor;
import ai.timefold.solver.core.impl.util.MutableReference;

import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;

/**
 * Generates the bytecode for the MemberAccessor of a particular Member
 */
public final class GizmoMemberAccessorImplementor {

    final static String GENERIC_TYPE_FIELD = "genericType";
    final static String ANNOTATED_ELEMENT_FIELD = "annotatedElement";

    /**
     * Generates the constructor and implementations of {@link AbstractGizmoMemberAccessor} methods for the given
     * {@link Member}.
     *
     * @param className never null
     * @param classOutput never null, defines how to write the bytecode
     * @param memberInfo never null, member to generate MemberAccessor methods implementation for
     */
    public static void defineAccessorFor(String className, ClassOutput classOutput, GizmoMemberInfo memberInfo) {
        Class<? extends AbstractGizmoMemberAccessor> superClass = getCorrectSuperclass(memberInfo);
        try (ClassCreator classCreator = ClassCreator.builder()
                .className(className)
                .superClass(superClass)
                .classOutput(classOutput)
                .setFinal(true)
                .build()) {
            classCreator.getFieldCreator("genericType", Type.class)
                    .setModifiers(Modifier.FINAL);
            classCreator.getFieldCreator("annotatedElement", AnnotatedElement.class)
                    .setModifiers(Modifier.FINAL);

            // ************************************************************************
            // MemberAccessor methods
            // ************************************************************************
            createConstructor(classCreator, memberInfo);
            createGetDeclaringClass(classCreator, memberInfo);
            createGetType(classCreator, memberInfo);
            createGetGenericType(classCreator);
            createGetName(classCreator, memberInfo);
            createExecuteGetter(classCreator, memberInfo);
            if (superClass == AbstractReadWriteGizmoMemberAccessor.class) {
                createExecuteSetter(classCreator, memberInfo);
            }
            createGetAnnotation(classCreator);
            createDeclaredAnnotationsByType(classCreator);
        }
    }

    private static Class<? extends AbstractGizmoMemberAccessor> getCorrectSuperclass(GizmoMemberInfo memberInfo) {
        AtomicBoolean supportsSetter = new AtomicBoolean();
        memberInfo.descriptor().whenIsMethod(method -> {
            supportsSetter.set(memberInfo.descriptor().getSetter().isPresent());
        });
        memberInfo.descriptor().whenIsField(field -> {
            supportsSetter.set(true);
        });
        if (supportsSetter.get()) {
            return AbstractReadWriteGizmoMemberAccessor.class;
        } else {
            return AbstractReadOnlyGizmoMemberAccessor.class;
        }
    }

    /**
     * Creates a MemberAccessor for a given member, generating
     * the MemberAccessor bytecode if required
     *
     * @param member The member to generate a MemberAccessor for
     * @param annotationClass The annotation it was annotated with (used for
     *        error reporting)
     * @param returnTypeRequired A flag that indicates if the return type is required or optional
     * @param gizmoClassLoader never null
     * @return A new MemberAccessor that uses Gizmo generated bytecode.
     *         Will generate the bytecode the first type it is called
     *         for a member, unless a classloader has been set,
     *         in which case no Gizmo code will be generated.
     */
    static MemberAccessor createAccessorFor(Member member, Class<? extends Annotation> annotationClass,
            boolean returnTypeRequired, GizmoClassLoader gizmoClassLoader) {
        String className = GizmoMemberAccessorFactory.getGeneratedClassName(member);
        if (gizmoClassLoader.hasBytecodeFor(className)) {
            return createInstance(className, gizmoClassLoader);
        }
        final MutableReference<byte[]> classBytecodeHolder = new MutableReference<>(null);
        ClassOutput classOutput = (path, byteCode) -> classBytecodeHolder.setValue(byteCode);
        GizmoMemberInfo memberInfo =
                new GizmoMemberInfo(new GizmoMemberDescriptor(member), returnTypeRequired, annotationClass);
        defineAccessorFor(className, classOutput, memberInfo);
        byte[] classBytecode = classBytecodeHolder.getValue();

        gizmoClassLoader.storeBytecode(className, classBytecode);
        return createInstance(className, gizmoClassLoader);
    }

    private static MemberAccessor createInstance(String className, GizmoClassLoader gizmoClassLoader) {
        try {
            return (MemberAccessor) gizmoClassLoader.loadClass(className)
                    .getConstructor().newInstance();
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | ClassNotFoundException
                | NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    // ************************************************************************
    // MemberAccessor methods
    // ************************************************************************

    private static MethodCreator getMethodCreator(ClassCreator classCreator, Class<?> returnType, String methodName,
            Class<?>... parameters) {
        return classCreator.getMethodCreator(methodName, returnType, parameters);
    }

    private static void createConstructor(ClassCreator classCreator, GizmoMemberInfo memberInfo) {
        MethodCreator methodCreator =
                classCreator.getMethodCreator(MethodDescriptor.ofConstructor(classCreator.getClassName()));

        ResultHandle thisObj = methodCreator.getThis();

        // Invoke Object's constructor
        methodCreator.invokeSpecialMethod(MethodDescriptor.ofConstructor(classCreator.getSuperClass()), thisObj);

        ResultHandle declaringClass = methodCreator.loadClass(memberInfo.descriptor().getDeclaringClassName());
        memberInfo.descriptor().whenMetadataIsOnField(fd -> {
            ResultHandle name = methodCreator.load(fd.getName());
            ResultHandle field = methodCreator.invokeVirtualMethod(MethodDescriptor.ofMethod(Class.class, "getDeclaredField",
                    Field.class, String.class),
                    declaringClass, name);
            ResultHandle type =
                    methodCreator.invokeVirtualMethod(MethodDescriptor.ofMethod(Field.class, "getGenericType", Type.class),
                            field);
            methodCreator.writeInstanceField(FieldDescriptor.of(classCreator.getClassName(), GENERIC_TYPE_FIELD, Type.class),
                    thisObj, type);
            methodCreator.writeInstanceField(
                    FieldDescriptor.of(classCreator.getClassName(), ANNOTATED_ELEMENT_FIELD, AnnotatedElement.class),
                    thisObj, field);
        });

        memberInfo.descriptor().whenMetadataIsOnMethod(md -> {
            ResultHandle name = methodCreator.load(md.getName());
            ResultHandle method = methodCreator.invokeVirtualMethod(MethodDescriptor.ofMethod(Class.class, "getDeclaredMethod",
                    Method.class, String.class, Class[].class),
                    declaringClass, name,
                    methodCreator.newArray(Class.class, 0));
            if (memberInfo.returnTypeRequired()) {
                // We create a field to store the result, only if the called method has a return type.
                // Otherwise, we will only execute it
                ResultHandle type =
                        methodCreator.invokeVirtualMethod(
                                MethodDescriptor.ofMethod(Method.class, "getGenericReturnType", Type.class),
                                method);
                methodCreator.writeInstanceField(
                        FieldDescriptor.of(classCreator.getClassName(), GENERIC_TYPE_FIELD, Type.class),
                        thisObj, type);
            }
            methodCreator.writeInstanceField(
                    FieldDescriptor.of(classCreator.getClassName(), ANNOTATED_ELEMENT_FIELD, AnnotatedElement.class),
                    thisObj, method);
        });

        // Return this (it a constructor)
        methodCreator.returnValue(thisObj);
    }

    /**
     * Generates the following code:
     *
     * <pre>
     * Class getDeclaringClass() {
     *     return ClassThatDeclaredMember.class;
     * }
     * </pre>
     */
    private static void createGetDeclaringClass(ClassCreator classCreator, GizmoMemberInfo memberInfo) {
        MethodCreator methodCreator = getMethodCreator(classCreator, Class.class, "getDeclaringClass");
        ResultHandle out = methodCreator.loadClass(memberInfo.descriptor().getDeclaringClassName());
        methodCreator.returnValue(out);
    }

    /**
     * Asserts method is a getter or read method
     *
     * @param method Method to assert is getter or read
     * @param returnTypeRequired Flag used to check method return type
     */
    private static void assertIsGoodMethod(MethodDescriptor method, boolean returnTypeRequired) {
        // V = void return type
        // Z = primitive boolean return type
        String methodName = method.getName();
        if (method.getParameterTypes().length != 0) {
            // not read or getter method
            throw new IllegalStateException("The getterMethod (%s) must not have any parameters, but has parameters (%s)."
                    .formatted(methodName, Arrays.toString(method.getParameterTypes())));
        }
        if (methodName.startsWith("get")) {
            if (method.getReturnType().equals("V")) {
                throw new IllegalStateException("The getterMethod (%s) must have a non-void return type."
                        .formatted(methodName));
            }
        } else if (methodName.startsWith("is")) {
            if (!method.getReturnType().equals("Z")) {
                throw new IllegalStateException("""
                        The getterMethod (%s) must have a primitive boolean return type but returns (%s).
                        Maybe rename the method (get%s)?"""
                        .formatted(methodName, method.getReturnType(), methodName.substring(2)));
            }
        } else {
            // must be a read method
            if (returnTypeRequired && method.getReturnType().equals("V")) {
                throw new IllegalStateException("The readMethod (%s) must have a non-void return type."
                        .formatted(methodName));
            }
        }
    }

    /**
     * Asserts method is a getter or read method
     *
     * @param method Method to assert is getter or read
     * @param returnTypeRequired Flag used to check method return type
     * @param annotationClass Used in exception message
     */
    private static void assertIsGoodMethod(MethodDescriptor method, boolean returnTypeRequired,
            Class<? extends Annotation> annotationClass) {
        // V = void return type
        // Z = primitive boolean return type
        String methodName = method.getName();
        if (method.getParameterTypes().length != 0) {
            // not read or getter method
            throw new IllegalStateException(
                    "The getterMethod (%s) with a %s annotation must not have any parameters, but has parameters (%s)."
                            .formatted(methodName, annotationClass.getSimpleName(),
                                    Arrays.toString(method.getParameterTypes())));
        }
        if (methodName.startsWith("get")) {
            if (method.getReturnType().equals("V")) {
                throw new IllegalStateException("The getterMethod (%s) with a %s annotation must have a non-void return type."
                        .formatted(methodName, annotationClass.getSimpleName()));
            }
        } else if (methodName.startsWith("is")) {
            if (!method.getReturnType().equals("Z")) {
                throw new IllegalStateException("""
                        The getterMethod (%s) with a %s annotation must have a primitive boolean return type but returns (%s).
                        Maybe rename the method (get%s)?"""
                        .formatted(methodName, annotationClass.getSimpleName(), method.getReturnType(),
                                methodName.substring(2)));
            }
        } else {
            // must be a read method and return a result only if returnTypeRequired is true
            if (returnTypeRequired && method.getReturnType().equals("V")) {
                throw new IllegalStateException("The readMethod (%s) with a %s annotation must have a non-void return type."
                        .formatted(methodName, annotationClass.getSimpleName()));
            }
        }
    }

    /**
     * Generates the following code:
     *
     * <pre>
     * String getName() {
     *     return "fieldOrMethodName";
     * }
     * </pre>
     *
     * If it is a getter method, "get" is removed and the first
     * letter become lowercase
     */
    private static void createGetName(ClassCreator classCreator, GizmoMemberInfo memberInfo) {
        MethodCreator methodCreator = getMethodCreator(classCreator, String.class, "getName");

        // If it is a method, assert that it has the required
        // properties
        memberInfo.descriptor().whenIsMethod(method -> {
            var annotationClass = memberInfo.annotationClass();
            if (annotationClass == null) {
                assertIsGoodMethod(method, memberInfo.returnTypeRequired());
            } else {
                assertIsGoodMethod(method, memberInfo.returnTypeRequired(), annotationClass);
            }
        });

        String fieldName = memberInfo.descriptor().getName();
        ResultHandle out = methodCreator.load(fieldName);
        methodCreator.returnValue(out);
    }

    /**
     * Generates the following code:
     *
     * <pre>
     * Class getType() {
     *     return FieldTypeOrMethodReturnType.class;
     * }
     * </pre>
     */
    private static void createGetType(ClassCreator classCreator, GizmoMemberInfo memberInfo) {
        MethodCreator methodCreator = getMethodCreator(classCreator, Class.class, "getType");
        ResultHandle out = methodCreator.loadClass(memberInfo.descriptor().getTypeName());
        methodCreator.returnValue(out);
    }

    /**
     * Generates the following code:
     *
     * <pre>
     * Type getGenericType() {
     *     return GizmoMemberAccessorImplementor.getGenericTypeFor(this.getClass().getName());
     * }
     * </pre>
     *
     * We are unable to load a non-primitive object constant, so we need to store it
     * in the implementor, which then can return us the Type when needed. The type
     * is stored in gizmoMemberAccessorNameToGenericType when this method is called.
     */
    private static void createGetGenericType(ClassCreator classCreator) {
        MethodCreator methodCreator = getMethodCreator(classCreator, Type.class, "getGenericType");
        ResultHandle thisObj = methodCreator.getThis();

        ResultHandle out =
                methodCreator.readInstanceField(FieldDescriptor.of(classCreator.getClassName(), GENERIC_TYPE_FIELD, Type.class),
                        thisObj);
        methodCreator.returnValue(out);
    }

    /**
     * Generates the following code:
     *
     * For a field
     *
     * <pre>
     * Object executeGetter(Object bean) {
     *     return ((DeclaringClass) bean).field;
     * }
     * </pre>
     *
     * For a method with returning type
     *
     * <pre>
     * Object executeGetter(Object bean) {
     *     return ((DeclaringClass) bean).method();
     * }
     * </pre>
     *
     * For a method without returning type
     *
     * <pre>
     * Object executeGetter(Object bean) {
     *     ((DeclaringClass) bean).method();
     *     return null;
     * }
     * </pre>
     *
     * The member MUST be public if not called in Quarkus
     * (i.e. we don't delegate to the field getter/setter).
     * In Quarkus, we generate simple getter/setter for the
     * member if it is private (which get passed to the MemberDescriptor).
     */
    private static void createExecuteGetter(ClassCreator classCreator, GizmoMemberInfo memberInfo) {
        MethodCreator methodCreator = getMethodCreator(classCreator, Object.class, "executeGetter", Object.class);
        ResultHandle bean = methodCreator.getMethodParam(0);
        if (memberInfo.returnTypeRequired()) {
            methodCreator.returnValue(memberInfo.descriptor().readMemberValue(methodCreator, bean));
        } else {
            memberInfo.descriptor().readMemberValue(methodCreator, bean);
            // Returns null as the called method has no return type
            methodCreator.returnNull();
        }
    }

    /**
     * Generates the following code:
     *
     * For a field
     *
     * <pre>
     * void executeSetter(Object bean, Object value) {
     *     return ((DeclaringClass) bean).field = value;
     * }
     * </pre>
     *
     * For a getter method with a corresponding setter
     *
     * <pre>
     * void executeSetter(Object bean, Object value) {
     *     return ((DeclaringClass) bean).setValue(value);
     * }
     * </pre>
     *
     * For a read method or a getter method without a setter
     *
     * <pre>
     * void executeSetter(Object bean, Object value) {
     *     throw new UnsupportedOperationException("Setter not supported");
     * }
     * </pre>
     */
    private static void createExecuteSetter(ClassCreator classCreator, GizmoMemberInfo memberInfo) {
        MethodCreator methodCreator = getMethodCreator(classCreator, void.class, "executeSetter", Object.class,
                Object.class);

        ResultHandle bean = methodCreator.getMethodParam(0);
        ResultHandle value = methodCreator.getMethodParam(1);
        if (memberInfo.descriptor().writeMemberValue(methodCreator, bean, value)) {
            // we are here only if the write is successful
            methodCreator.returnValue(null);
        } else {
            methodCreator.throwException(UnsupportedOperationException.class, "Setter not supported");
        }
    }

    private static MethodCreator getAnnotationMethodCreator(ClassCreator classCreator, Class<?> returnType, String methodName,
            Class<?>... parameters) {
        return classCreator.getMethodCreator(getAnnotationMethod(returnType, methodName, parameters));
    }

    private static MethodDescriptor getAnnotationMethod(Class<?> returnType, String methodName, Class<?>... parameters) {
        return MethodDescriptor.ofMethod(AnnotatedElement.class, methodName, returnType, parameters);
    }

    /**
     * Generates the following code:
     *
     * <pre>
     * Object getAnnotation(Class annotationClass) {
     *     AnnotatedElement annotatedElement = GizmoMemberAccessorImplementor
     *             .getAnnotatedElementFor(this.getClass().getName());
     *     return annotatedElement.getAnnotation(annotationClass);
     * }
     * </pre>
     */
    private static void createGetAnnotation(ClassCreator classCreator) {
        MethodCreator methodCreator = getAnnotationMethodCreator(classCreator, Annotation.class, "getAnnotation", Class.class);
        ResultHandle thisObj = methodCreator.getThis();

        ResultHandle annotatedElement = methodCreator.readInstanceField(
                FieldDescriptor.of(classCreator.getClassName(), ANNOTATED_ELEMENT_FIELD, AnnotatedElement.class),
                thisObj);
        ResultHandle query = methodCreator.getMethodParam(0);
        ResultHandle out =
                methodCreator.invokeInterfaceMethod(getAnnotationMethod(Annotation.class, "getAnnotation", Class.class),
                        annotatedElement, query);
        methodCreator.returnValue(out);
    }

    private static void createDeclaredAnnotationsByType(ClassCreator classCreator) {
        MethodCreator methodCreator =
                getAnnotationMethodCreator(classCreator, Annotation[].class, "getDeclaredAnnotationsByType", Class.class);
        ResultHandle thisObj = methodCreator.getThis();

        ResultHandle annotatedElement = methodCreator.readInstanceField(
                FieldDescriptor.of(classCreator.getClassName(), ANNOTATED_ELEMENT_FIELD, AnnotatedElement.class),
                thisObj);
        ResultHandle query = methodCreator.getMethodParam(0);
        ResultHandle out = methodCreator.invokeInterfaceMethod(
                getAnnotationMethod(Annotation[].class, "getDeclaredAnnotationsByType", Class.class),
                annotatedElement, query);
        methodCreator.returnValue(out);
    }

    private GizmoMemberAccessorImplementor() {

    }

}
