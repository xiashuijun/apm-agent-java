/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.plugin.api;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.bci.VisibleForAdvice;
import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.impl.transaction.AbstractSpan;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.impl.transaction.TransactionId;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import static co.elastic.apm.plugin.api.ElasticApmApiInstrumentation.PUBLIC_API_INSTRUMENTATION_GROUP;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Injects the actual implementation of the public API class co.elastic.apm.api.SpanImpl.
 */
public class SpanInstrumentation extends ElasticApmInstrumentation {

    private final ElementMatcher<? super MethodDescription> methodMatcher;

    public SpanInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.api.SpanImpl");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    @Override
    public boolean includeWhenInstrumentationIsDisabled() {
        return true;
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(PUBLIC_API_INSTRUMENTATION_GROUP);
    }

    public static class SetNameInstrumentation extends SpanInstrumentation {
        public SetNameInstrumentation() {
            super(named("setName"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void setName(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                   @Advice.Argument(0) String name) {
            span.setName(name);
        }
    }

    public static class SetTypeInstrumentation extends SpanInstrumentation {
        public SetTypeInstrumentation() {
            super(named("setType"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void setType(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                   @Advice.Argument(0) String type) {
            span.withType(type);
        }
    }

    public static class DoCreateSpanInstrumentation extends SpanInstrumentation {
        public DoCreateSpanInstrumentation() {
            super(named("doCreateSpan"));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit
        public static void doCreateSpan(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                        @Advice.Return(readOnly = false) Object result) {
            result = span.createSpan();
        }
    }

    public static class EndInstrumentation extends SpanInstrumentation {
        public EndInstrumentation() {
            super(named("end"));
        }

        @VisibleForAdvice
        @Advice.OnMethodEnter
        public static void end(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span) {
            span.end();
        }
    }


    public static class CaptureExceptionInstrumentation extends SpanInstrumentation {
        public CaptureExceptionInstrumentation() {
            super(named("captureException").and(takesArguments(Throwable.class)));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit
        public static void doCreateSpan(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                        @Advice.Argument(0) Throwable t) {
            span.captureException(t);
        }
    }

    public static class GetIdInstrumentation extends SpanInstrumentation {
        public GetIdInstrumentation() {
            super(named("getId").and(takesArguments(0)));
        }

        @VisibleForAdvice
        @Advice.OnMethodExit
        public static void doCreateSpan(@Advice.FieldValue(value = "span", typing = Assigner.Typing.DYNAMIC) AbstractSpan<?> span,
                                        @Advice.Return(readOnly = false) String id) {
            if (tracer != null) {
                if (tracer.getConfig(CoreConfiguration.class).isDistributedTracingEnabled()) {
                    id = span.getTraceContext().getId().toString();
                } else {
                    if (span instanceof Span) {
                        id = Long.toString(((Span) span).getId().asLong());
                    } else if (span instanceof Transaction) {
                        id = ((Transaction) span).getId().toUUID().toString();
                    }
                }
            }
        }
    }
}
