package io.mockk.proxy.jvm;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.*;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Callable;
import io.mockk.agent.MockKInvocationHandler;

import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;

@SuppressWarnings({"unused", "UnusedAssignment"})
public class MockKStaticProxyAdvice extends JvmMockKProxyDispatcher {
    public MockKStaticProxyAdvice(Map<Object, MockKInvocationHandler> handlers) {
        super(handlers);
    }

    @OnMethodEnter(skipOn = OnNonDefaultValue.class)
    private static Callable<?> enterStatic(@MockKProxyAdviceId long id,
                                           @Origin final Method method,
                                           @AllArguments final Object[] arguments) throws Throwable {
        Object self = method.getDeclaringClass();
        JvmMockKDispatcher dispatcher = JvmMockKDispatcher.get(id, self);
        if (dispatcher == null) {
            return null;
        }
        return dispatcher.handler(self, method, arguments);
    }

    @OnMethodExit
    private static void exit(@Advice.Return(readOnly = false, typing = DYNAMIC) Object returned,
                             @Advice.Enter Callable<?> mocked) throws Throwable {
        if (mocked != null) {
            returned = mocked.call();
        }
    }

}
