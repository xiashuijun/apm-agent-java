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
package co.elastic.apm.impl.transaction;

import co.elastic.apm.impl.sampling.ConstantSampler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextTest {

    @Test
    void parseFromTraceParentHeader_notRecorded_notRequested() {
        final TraceContext traceContext = new TraceContext();
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00";
        traceContext.asChildOf(header);
        assertThat(traceContext.isSampled()).isFalse();
        // if the parent did not record and tracing this request was not requested,
        // we should not record this request and
        // propagate downstream that we have not recorded and that tracing has not been requested
        // -> 0000 0000 binary, 00 hex
        assertThat(traceContext.getOutgoingTraceParentHeader()).endsWith("-00");
    }

    @Test
    void parseFromTraceParentHeader_recorded_notRequested() {
        final TraceContext traceContext = new TraceContext();
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-02";
        traceContext.asChildOf(header);
        assertThat(traceContext.isSampled()).isTrue();
        // if the parent recorded, but tracing this request was not requested,
        // we should also record this request (deferred tracing decision) and
        // propagate downstream that we have recorded, but tracing has not been requested
        // -> 0000 0010 binary, 02 hex
        assertThat(traceContext.getOutgoingTraceParentHeader()).endsWith("-02");
    }

    @Test
    void parseFromTraceParentHeader_notRecorded_requested() {
        final TraceContext traceContext = new TraceContext();
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01";
        traceContext.asChildOf(header);
        assertThat(traceContext.isSampled()).isTrue();
        // if the parent did not record, but tracing the request is requested (maybe due to rate limiting),
        // we should trace this request and
        // propagate downstream that we have recorded and tracing is requested
        // -> 0000 0011 binary, 03 hex
        // it also means that we have an incomplete trace
        assertThat(traceContext.getOutgoingTraceParentHeader()).endsWith("-03");
    }

    @Test
    void parseFromTraceParentHeader_recorded_requested() {
        final TraceContext traceContext = new TraceContext();
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-03";
        traceContext.asChildOf(header);
        assertThat(traceContext.isSampled()).isTrue();
        // if the parent recorded, and tracing the request is requested,
        // we should just sample and
        // propagate the same flags downstream
        // -> 0000 0011 binary, 03 hex
        assertThat(traceContext.getOutgoingTraceParentHeader()).endsWith("-03");
    }

    @Test
    void outgoingHeader() {
        final TraceContext traceContext = new TraceContext();
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-03";
        traceContext.asChildOf(header);
        assertThat(traceContext.getOutgoingTraceParentHeader().toString())
            .isEqualTo("00-0af7651916cd43dd8448eb211c80319c-"+ traceContext.getId().toString() + "-03");
    }

    @Test
    void outgoingHeaderRootSpan() {
        final TraceContext traceContext = new TraceContext();
        traceContext.asRootSpan(ConstantSampler.of(true));
        assertThat(traceContext.getOutgoingTraceParentHeader().toString()).hasSize(55);
        assertThat(traceContext.getOutgoingTraceParentHeader().toString()).startsWith("00-");
        assertThat(traceContext.getOutgoingTraceParentHeader().toString()).endsWith("-03");
    }

    @Test
    void parseFromTraceParentHeader_notSampled() {
        final TraceContext traceContext = new TraceContext();
        final String header = "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00";
        traceContext.asChildOf(header);
        assertThat(traceContext.isSampled()).isFalse();
        assertThat(traceContext.getIncomingTraceParentHeader()).isEqualTo(header);
    }

    @Test
    void testResetState() {
        final TraceContext traceContext = new TraceContext();
        traceContext.asChildOf("00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-00");
        traceContext.resetState();
        assertThat(traceContext.getIncomingTraceParentHeader()).isEqualTo("00-00000000000000000000000000000000-0000000000000000-00");
    }

    @Test
    void testRandomValue() {
        final TraceContext traceContext = new TraceContext();
        traceContext.asRootSpan(ConstantSampler.of(true));
        assertThat(traceContext.getTraceId().isEmpty()).isFalse();
        assertThat(traceContext.getParentId().asLong()).isZero();
        assertThat(traceContext.getId().asLong()).isNotZero();
        assertThat(traceContext.isSampled()).isTrue();
    }

    @Test
    void testSetSampled() {
        final TraceContext traceContext = new TraceContext();
        traceContext.asRootSpan(ConstantSampler.of(false));
        assertThat(traceContext.isSampled()).isFalse();
        traceContext.setRecorded(true);
        assertThat(traceContext.isSampled()).isTrue();
        traceContext.setRecorded(false);
        assertThat(traceContext.isSampled()).isFalse();
    }

    @Test
    void testInvalidHeader() {
        final TraceContext traceContext = new TraceContext();
        traceContext.asChildOf("01-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-03");
        assertThat(traceContext.getTraceId().isEmpty()).isTrue();
        assertThat(traceContext.getParentId().asLong()).isZero();
        assertThat(traceContext.getId().asLong()).isZero();
        assertThat(traceContext.isSampled()).isFalse();
    }
}
