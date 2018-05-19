package io.mockk.proxy.jvm

import io.mockk.agent.*
import io.mockk.proxy.jvm.transformation.InlineInstrumentation
import io.mockk.proxy.jvm.transformation.TransformationRequest
import io.mockk.proxy.jvm.transformation.TransformationType

internal class ConstructorProxyMaker(
    private val log: MockKAgentLogger,
    private val inliner: InlineInstrumentation?,
    private val constructorHandlers: MutableMap<Any, MockKInvocationHandler>
) : MockKConstructorProxyMaker {

    override fun constructorProxy(
        clazz: Class<*>,
        handler: MockKInvocationHandler
    ): Cancelable<Class<*>> {
        if (inliner == null) {
            throw MockKAgentException(
                "Failed to create constructor proxy for $clazz.\n" +
                        "Try running VM with MockK Java Agent\n" +
                        "i.e. with -javaagent:mockk-agent.jar option."
            )
        }

        constructorHandlers[clazz] = handler

        val cancellation = inliner.execute(
            TransformationRequest(
                setOf(clazz),
                TransformationType.CONSTRUCTOR
            )
        )

        return CancelableResult(clazz, cancellation)
            .alsoOnCancel {
                constructorHandlers.remove(clazz)
            }
    }
}
