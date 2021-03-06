package io.mockk.impl.instantiation

import io.mockk.*
import io.mockk.impl.InternalPlatform
import io.mockk.impl.stub.Stub
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.Callable
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

object JvmMockFactoryHelper {
    fun mockHandler(stub: Stub): (Any, Method, Callable<*>?, Array<Any?>) -> Any? {
        return { self, method, originalMethod, args ->
            stdFunctions(self, method, args) {

                stub.handleInvocation(
                    self,
                    method.toDescription(), {
                        handleOriginalCall(originalMethod, method)
                    },
                    args,
                    findBackingField(self, method)
                )
            }
        }
    }

    private fun findBackingField(self: Any, method: Method): BackingFieldValueProvider {
        return {
            val property = self::class.memberProperties.firstOrNull {
                it.getter.javaMethod == method ||
                        (it is KMutableProperty<*> && it.setter.javaMethod == method)
            }


            property?.javaField?.let { field ->
                BackingFieldValue(
                    property.name,
                    {
                        InternalPlatformDsl.makeAccessible(field);
                        field.get(self)
                    },
                    {
                        InternalPlatformDsl.makeAccessible(field);
                        field.set(self, it)
                    }
                )
            }
        }
    }

    private inline fun stdFunctions(
        self: Any,
        method: Method,
        args: Array<Any?>,
        otherwise: () -> Any?
    ): Any? {
        if (self is Class<*>) {
            if (method.isHashCode()) {
                return System.identityHashCode(self)
            } else if (method.isEquals()) {
                return self === args[0]
            }
        }
        return otherwise()
    }

    private fun handleOriginalCall(originalMethod: Callable<*>?, method: Method): Any? {
        if (originalMethod == null) {
            throw MockKException("No way to call original method ${method.toDescription()}")
        }

        return try {
            originalMethod.call()
        } catch (ex: InvocationTargetException) {
            throw ex.cause ?: throw ex
        }
    }

    private fun Method.toDescription() =
        MethodDescription(
            name,
            returnType.kotlin,
            declaringClass.kotlin,
            parameterTypes.map { it.kotlin },
            varArgPosition(),
            Modifier.isPrivate(modifiers) ||
                    Modifier.isProtected(modifiers)
        )

    fun Method.isHashCode() = name == "hashCode" && parameterTypes.isEmpty()
    fun Method.isEquals() = name == "equals" && parameterTypes.size == 1 && parameterTypes[0] === Object::class.java


    fun Method.varArgPosition(): Int {
        val kFunc =
            try {
                // workaround for
                //  https://github.com/mockk/mockk/issues/18
                //  https://github.com/mockk/mockk/issues/22
                kotlinFunction
            } catch (ex: Throwable) {
                null
            }

        return if (kFunc != null)
            kFunc.parameters
                .filter { it.kind != KParameter.Kind.INSTANCE }
                .indexOfFirst { it.isVararg }
        else
            if (isVarArgs) parameterTypes.size - 1 else -1
    }
}
