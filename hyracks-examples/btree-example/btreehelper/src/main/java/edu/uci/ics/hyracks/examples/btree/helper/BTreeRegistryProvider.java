/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uci.ics.hyracks.examples.btree.helper;

import edu.uci.ics.hyracks.api.context.IHyracksStageletContext;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTree;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IIndexRegistryProvider;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IndexRegistry;

public class BTreeRegistryProvider implements IIndexRegistryProvider<BTree> {
    private static final long serialVersionUID = 1L;

    public static final BTreeRegistryProvider INSTANCE = new BTreeRegistryProvider();

    private BTreeRegistryProvider() {
    }

    @Override
    public IndexRegistry<BTree> getRegistry(IHyracksStageletContext ctx) {
        return RuntimeContext.get(ctx).getBTreeRegistry();
    }
}