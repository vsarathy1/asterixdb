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
package edu.uci.ics.hyracks.examples.btree.client;

import java.util.UUID;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import edu.uci.ics.hyracks.api.client.HyracksRMIConnection;
import edu.uci.ics.hyracks.api.client.IHyracksClientConnection;
import edu.uci.ics.hyracks.api.constraints.PartitionConstraintHelper;
import edu.uci.ics.hyracks.api.dataflow.IConnectorDescriptor;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryHashFunctionFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.dataflow.value.ITypeTrait;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.dataflow.value.TypeTrait;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.dataflow.common.data.comparators.IntegerBinaryComparatorFactory;
import edu.uci.ics.hyracks.dataflow.common.data.hash.UTF8StringBinaryHashFunctionFactory;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.IntegerSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.UTF8StringSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.partition.FieldHashPartitionComputerFactory;
import edu.uci.ics.hyracks.dataflow.std.connectors.MToNHashPartitioningConnectorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.connectors.OneToOneConnectorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.IFileSplitProvider;
import edu.uci.ics.hyracks.dataflow.std.sort.ExternalSortOperatorDescriptor;
import edu.uci.ics.hyracks.examples.btree.helper.BTreeRegistryProvider;
import edu.uci.ics.hyracks.examples.btree.helper.DataGenOperatorDescriptor;
import edu.uci.ics.hyracks.examples.btree.helper.StorageManagerInterface;
import edu.uci.ics.hyracks.storage.am.btree.api.IBTreeInteriorFrameFactory;
import edu.uci.ics.hyracks.storage.am.btree.api.IBTreeLeafFrameFactory;
import edu.uci.ics.hyracks.storage.am.btree.dataflow.BTreeBulkLoadOperatorDescriptor;
import edu.uci.ics.hyracks.storage.am.btree.frames.NSMInteriorFrameFactory;
import edu.uci.ics.hyracks.storage.am.btree.frames.NSMLeafFrameFactory;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTree;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IIndexRegistryProvider;
import edu.uci.ics.hyracks.storage.am.common.tuples.TypeAwareTupleWriterFactory;
import edu.uci.ics.hyracks.storage.common.IStorageManagerInterface;

// This example will load a primary index from randomly generated data

public class PrimaryIndexBulkLoadExample {
    private static class Options {
        @Option(name = "-host", usage = "Hyracks Cluster Controller Host name", required = true)
        public String host;

        @Option(name = "-port", usage = "Hyracks Cluster Controller Port (default: 1099)")
        public int port = 1099;

        @Option(name = "-app", usage = "Hyracks Application name", required = true)
        public String app;

        @Option(name = "-target-ncs", usage = "Comma separated list of node-controller names to use", required = true)
        public String ncs;

        @Option(name = "-num-tuples", usage = "Total number of tuples to to be generated for loading", required = true)
        public int numTuples;

        @Option(name = "-btreename", usage = "B-Tree file name", required = true)
        public String btreeName;

        @Option(name = "-sortbuffer-size", usage = "Sort buffer size in frames (default: 32768)", required = false)
        public int sbSize = 32768;
    }

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        CmdLineParser parser = new CmdLineParser(options);
        parser.parseArgument(args);

        IHyracksClientConnection hcc = new HyracksRMIConnection(options.host, options.port);

        JobSpecification job = createJob(options);

        long start = System.currentTimeMillis();
        UUID jobId = hcc.createJob(options.app, job);
        hcc.start(jobId);
        hcc.waitForCompletion(jobId);
        long end = System.currentTimeMillis();
        System.err.println(start + " " + end + " " + (end - start));
    }

    private static JobSpecification createJob(Options options) {

        JobSpecification spec = new JobSpecification();

        String[] splitNCs = options.ncs.split(",");

        // schema of tuples to be generated: 5 fields with string, string, int,
        // int, string
        // we will use field-index 2 as primary key to fill a clustered index
        RecordDescriptor recDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                UTF8StringSerializerDeserializer.INSTANCE, // this field will
                                                           // not go into B-Tree
                UTF8StringSerializerDeserializer.INSTANCE, // we will use this
                                                           // as payload
                IntegerSerializerDeserializer.INSTANCE, // we will use this
                                                        // field as key
                IntegerSerializerDeserializer.INSTANCE, // we will use this as
                                                        // payload
                UTF8StringSerializerDeserializer.INSTANCE // we will use this as
                                                          // payload
                });

        // generate numRecords records with field 2 being unique, integer values
        // in [0, 100000], and strings with max length of 10 characters, and
        // random seed 50
        DataGenOperatorDescriptor dataGen = new DataGenOperatorDescriptor(spec, recDesc, options.numTuples, 2, 0,
                100000, 10, 50);
        // run data generator on first nodecontroller given
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, dataGen, splitNCs[0]);

        // sort the tuples as preparation for bulk load
        // fields to sort on
        int[] sortFields = { 2 };
        // comparators for sort fields
        IBinaryComparatorFactory[] comparatorFactories = new IBinaryComparatorFactory[1];
        comparatorFactories[0] = IntegerBinaryComparatorFactory.INSTANCE;
        ExternalSortOperatorDescriptor sorter = new ExternalSortOperatorDescriptor(spec, options.sbSize, sortFields,
                comparatorFactories, recDesc);
        JobHelper.createPartitionConstraint(spec, sorter, splitNCs);

        // tuples to be put into B-Tree shall have 4 fields
        int fieldCount = 4;
        ITypeTrait[] typeTraits = new ITypeTrait[fieldCount];
        typeTraits[0] = new TypeTrait(4);
        typeTraits[1] = new TypeTrait(ITypeTrait.VARIABLE_LENGTH);
        typeTraits[2] = new TypeTrait(4);
        typeTraits[3] = new TypeTrait(ITypeTrait.VARIABLE_LENGTH);

        // create factories and providers for B-Tree
        TypeAwareTupleWriterFactory tupleWriterFactory = new TypeAwareTupleWriterFactory(typeTraits);
        IBTreeInteriorFrameFactory interiorFrameFactory = new NSMInteriorFrameFactory(tupleWriterFactory);
        IBTreeLeafFrameFactory leafFrameFactory = new NSMLeafFrameFactory(tupleWriterFactory);
        IIndexRegistryProvider<BTree> btreeRegistryProvider = BTreeRegistryProvider.INSTANCE;
        IStorageManagerInterface storageManager = StorageManagerInterface.INSTANCE;

        // the B-Tree expects its keyfields to be at the front of its input
        // tuple
        int[] fieldPermutation = { 2, 1, 3, 4 }; // map field 2 of input tuple
                                                 // to field 0 of B-Tree tuple,
                                                 // etc.
        IFileSplitProvider btreeSplitProvider = JobHelper.createFileSplitProvider(splitNCs, options.btreeName);
        BTreeBulkLoadOperatorDescriptor btreeBulkLoad = new BTreeBulkLoadOperatorDescriptor(spec, storageManager,
                btreeRegistryProvider, btreeSplitProvider, interiorFrameFactory, leafFrameFactory, typeTraits,
                comparatorFactories, fieldPermutation, 0.7f);
        JobHelper.createPartitionConstraint(spec, btreeBulkLoad, splitNCs);

        // distribute the records from the datagen via hashing to the bulk load
        // ops
        IBinaryHashFunctionFactory[] hashFactories = new IBinaryHashFunctionFactory[1];
        hashFactories[0] = UTF8StringBinaryHashFunctionFactory.INSTANCE;
        IConnectorDescriptor hashConn = new MToNHashPartitioningConnectorDescriptor(spec,
                new FieldHashPartitionComputerFactory(new int[] { 0 }, hashFactories));

        spec.connect(hashConn, dataGen, 0, sorter, 0);

        spec.connect(new OneToOneConnectorDescriptor(spec), sorter, 0, btreeBulkLoad, 0);

        spec.addRoot(btreeBulkLoad);

        return spec;
    }
}