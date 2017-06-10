/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.hyracks.algebricks.runtime.base;

import org.apache.hyracks.algebricks.runtime.operators.std.NestedTupleSourceRuntimeFactory.NestedTupleSourceRuntime;
import org.apache.hyracks.api.comm.IFrameWriter;
import org.apache.hyracks.api.dataflow.EnforceFrameWriter;
import org.apache.hyracks.api.dataflow.value.RecordDescriptor;

public class EnforcePushRuntime extends EnforceFrameWriter implements IPushRuntime {

    private final IPushRuntime pushRuntime;

    private EnforcePushRuntime(IPushRuntime pushRuntime) {
        super(pushRuntime);
        this.pushRuntime = pushRuntime;
    }

    @Override
    public void setOutputFrameWriter(int index, IFrameWriter writer, RecordDescriptor recordDesc) {
        pushRuntime.setOutputFrameWriter(index, writer, recordDesc);
    }

    @Override
    public void setInputRecordDescriptor(int index, RecordDescriptor recordDescriptor) {
        pushRuntime.setInputRecordDescriptor(index, recordDescriptor);
    }

    public static IPushRuntime enforce(IPushRuntime pushRuntime) {
        return pushRuntime instanceof EnforcePushRuntime || pushRuntime instanceof NestedTupleSourceRuntime
                ? pushRuntime : new EnforcePushRuntime(pushRuntime);
    }
}
